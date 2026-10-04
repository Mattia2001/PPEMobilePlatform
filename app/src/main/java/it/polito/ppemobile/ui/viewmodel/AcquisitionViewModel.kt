package it.polito.ppemobile.ui.viewmodel

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.polito.ppemobile.domain.AcquisitionManager
import it.polito.ppemobile.inference.FrameProcessor
import it.polito.ppemobile.inference.LocalModelCatalog
import it.polito.ppemobile.metrics.DeviceMetricsSampler
import it.polito.ppemobile.models.Acquisition
import it.polito.ppemobile.models.AcquisitionConfig
import it.polito.ppemobile.models.DetectionResult
import it.polito.ppemobile.models.FrameResult
import it.polito.ppemobile.models.MetricsSnapshot
import it.polito.ppemobile.models.enums.AcquisitionState
import it.polito.ppemobile.models.enums.CVModel
import it.polito.ppemobile.models.enums.OffloadingStrategy
import it.polito.ppemobile.models.enums.ProcessingSegment
import it.polito.ppemobile.models.enums.Runtime
import it.polito.ppemobile.remote.RemoteInferenceClient
import it.polito.ppemobile.storage.AppSettingsStore
import it.polito.ppemobile.storage.StorageManager
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AcquisitionViewModel(application: Application) : AndroidViewModel(application) {

    private val metricsSampler = DeviceMetricsSampler(application.applicationContext)
    private val frameProcessor = FrameProcessor(application.applicationContext)
    private val acquisitionManager = AcquisitionManager()
    private val storageManager = StorageManager(application.applicationContext)
    private val appSettingsStore = AppSettingsStore(application.applicationContext)
    private val frameSequence = AtomicInteger(0)
    private val remoteRequestInFlight = AtomicBoolean(false)
    private val recentFrameTimes = ArrayDeque<Long>()
    private var activeConfiguration: AcquisitionConfig? = null
    private var remoteClient = RemoteInferenceClient(appSettingsStore.loadRemoteServer().baseUrl)

    var acquisitionState by mutableStateOf(AcquisitionState.IDLE)
        private set
    var metrics by mutableStateOf<MetricsSnapshot?>(null)
        private set
    var detectionResult by mutableStateOf<DetectionResult?>(null)
        private set
    var currentAcquisition by mutableStateOf<Acquisition?>(null)
        private set
    var frameCounter by mutableStateOf(0)
        private set
    var exportedZipFile by mutableStateOf<File?>(null)
        private set
    var modelReady by mutableStateOf(false)
        private set
    var inferenceError by mutableStateOf<String?>(null)
        private set
    var lastInferenceTimeMillis by mutableStateOf<Long?>(null)
        private set
    var processingFps by mutableStateOf<Float?>(null)
        private set

    private val frameResults = mutableStateListOf<FrameResult>()

    init {
        viewModelScope.launch {
            while (true) {
                metrics = metricsSampler.sample()
                delay(1000)
            }
        }
    }

    fun startAcquisition(configuration: AcquisitionConfig?) {
        if (configuration == null) return
        val unsupportedReason = when {
            configuration.offloadingStrategy == OffloadingStrategy.ALWAYS_LOCAL &&
                !LocalModelCatalog.isAvailable(configuration.cvModel) ->
                "The selected model artifact is not available in this build."
            configuration.offloadingStrategy == OffloadingStrategy.ALWAYS_LOCAL &&
                configuration.runtime != Runtime.TFLITE ->
                "Select TensorFlow Lite for local inference."
            configuration.offloadingStrategy == OffloadingStrategy.ALWAYS_OFFLOAD &&
                configuration.runtime != Runtime.REMOTE_SERVER ->
                "Select Remote Server for Always Offload."
            configuration.offloadingStrategy == OffloadingStrategy.ALWAYS_OFFLOAD &&
                configuration.cvModel != CVModel.YOLO26N_V2 ->
                "The server currently exposes YOLO26n Dataset v2 at 640 only."
            configuration.offloadingStrategy !in setOf(
                OffloadingStrategy.ALWAYS_LOCAL,
                OffloadingStrategy.ALWAYS_OFFLOAD
            ) -> "The selected adaptive strategy is not implemented yet."
            else -> null
        }
        if (unsupportedReason != null) {
            inferenceError = unsupportedReason
            return
        }

        val modelSelectionFailure = if (configuration.offloadingStrategy == OffloadingStrategy.ALWAYS_LOCAL) {
            runCatching { frameProcessor.selectModel(configuration.cvModel, configuration.localAcceleration) }.exceptionOrNull()
        } else {
            null
        }
        if (modelSelectionFailure != null) {
            inferenceError = "Model selection failed: ${modelSelectionFailure.message}"
            return
        }

        detectionResult = null
        currentAcquisition = null
        frameResults.clear()
        frameSequence.set(0)
        frameCounter = 0
        exportedZipFile = null
        lastInferenceTimeMillis = null
        processingFps = null
        recentFrameTimes.clear()
        remoteRequestInFlight.set(false)
        activeConfiguration = configuration
        remoteClient = RemoteInferenceClient(appSettingsStore.loadRemoteServer().baseUrl)
        modelReady = configuration.offloadingStrategy == OffloadingStrategy.ALWAYS_OFFLOAD
        inferenceError = null

        acquisitionManager.start(configuration)
        acquisitionState = AcquisitionState.RUNNING
        if (configuration.offloadingStrategy == OffloadingStrategy.ALWAYS_LOCAL) {
            warmUpModel(configuration.cvModel)
        }
    }

    fun stopAcquisition() {
        acquisitionState = AcquisitionState.STOPPED
        activeConfiguration = null
        val acquisition = acquisitionManager.stop()
        currentAcquisition = acquisition
        detectionResult = null

        if (acquisition != null) {
            exportedZipFile = storageManager.exportAcquisition(
                acquisition = acquisition,
                frames = acquisitionManager.getFrames()
            )
        }
    }

    /** Called serially by CameraX's dedicated analyzer executor. */
    fun processFrame(imageProxy: ImageProxy) {
        if (acquisitionState != AcquisitionState.RUNNING) return
        val configuration = activeConfiguration ?: return
        if (configuration.offloadingStrategy == OffloadingStrategy.ALWAYS_OFFLOAD) {
            processRemoteFrame(imageProxy, configuration)
        } else {
            processLocalFrame(imageProxy, configuration)
        }
    }

    private fun processLocalFrame(imageProxy: ImageProxy, configuration: AcquisitionConfig) {
        val frameIndex = frameSequence.getAndIncrement()
        val timestampGeneration = System.currentTimeMillis()
        val startedNanos = SystemClock.elapsedRealtimeNanos()

        try {
            val result = frameProcessor.processFrame(
                imageProxy = imageProxy,
                selectedPPEs = configuration.selectedPPEs.toSet()
            )
            val inferenceTime = (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000L
            val metricsSnapshot = metrics ?: metricsSampler.sample()

            viewModelScope.launch {
                if (acquisitionState != AcquisitionState.RUNNING) return@launch
                detectionResult = result
                inferenceError = null
                modelReady = true
                lastInferenceTimeMillis = inferenceTime
                updateLiveFps(SystemClock.elapsedRealtime())

                val frameResult = FrameResult(
                    frameId = "frame_${frameIndex.toString().padStart(6, '0')}",
                    timestampGeneration = timestampGeneration,
                    inferenceTime = inferenceTime,
                    displayTime = System.currentTimeMillis(),
                    imageReference = null,
                    processingSegment = ProcessingSegment.LOCAL,
                    detectionResult = result,
                    complexity = null,
                    inOrder = true,
                    metricsSnapshot = metricsSnapshot,
                    remoteInference = null
                )
                frameResults.add(frameResult)
                acquisitionManager.addFrame(frameResult)
                frameCounter = frameResults.size
                if (frameCounter == 1 || frameCounter % LOG_EVERY_FRAMES == 0) {
                    val diagnostics = result.inferenceDiagnostics
                    Log.i(
                        TAG,
                        "frame=${frameResult.frameId}, latency=${inferenceTime}ms, " +
                            "persons=${result.persons.size}, " +
                            "ppe=${result.persons.sumOf { it.ppeDetections.size }}, " +
                            "maxScores=${diagnostics?.maxConfidenceByClass}, " +
                            "candidates=${diagnostics?.candidatesAboveThreshold}, " +
                            "afterNms=${diagnostics?.detectionsAfterNms}"
                    )
                }
            }
        } catch (exception: Exception) {
            viewModelScope.launch {
                inferenceError = "Inference failed: ${exception.message ?: exception.javaClass.simpleName}"
                Log.e(TAG, "Frame inference failed", exception)
            }
        }
    }

    private fun processRemoteFrame(imageProxy: ImageProxy, configuration: AcquisitionConfig) {
        if (!remoteRequestInFlight.compareAndSet(false, true)) return
        val frameIndex = frameSequence.getAndIncrement()
        val frameId = "frame_${frameIndex.toString().padStart(6, '0')}"
        val timestampGeneration = System.currentTimeMillis()
        val pipelineStarted = SystemClock.elapsedRealtimeNanos()
        val encodedFrame = try {
            remoteClient.encodeFrame(imageProxy, configuration.compressionLevel)
        } catch (exception: Exception) {
            remoteRequestInFlight.set(false)
            inferenceError = "JPEG encoding failed: ${exception.message ?: exception.javaClass.simpleName}"
            Log.e(TAG, "Remote frame encoding failed", exception)
            return
        }
        val metricsAtCapture = metrics ?: metricsSampler.sample()
        val selectedClasses = buildList {
            add("person")
            configuration.selectedPPEs.forEach { ppe ->
                when (ppe.name) {
                    "HELMET" -> add("helmet")
                    "SAFETY_VEST" -> add("safety_vest")
                    "GLOVES" -> add("gloves")
                    "BOOTS", "SHOES" -> add("safety_boots")
                }
            }
        }.distinct()

        viewModelScope.launch {
            try {
                val remoteResult = withContext(Dispatchers.IO) {
                    remoteClient.infer(
                        encodedFrame = encodedFrame,
                        frameId = frameId,
                        sequenceNumber = frameIndex,
                        captureTimestampMs = timestampGeneration,
                        modelId = RemoteInferenceClient.YOLO26N_640_MODEL_ID,
                        selectedClasses = selectedClasses
                    )
                }
                if (acquisitionState != AcquisitionState.RUNNING) return@launch
                val endToEndMs = (SystemClock.elapsedRealtimeNanos() - pipelineStarted) / 1_000_000L
                val networkStatus = metricsAtCapture.networkStatus.copy(
                    rtt = remoteResult.metrics.roundTripMs,
                    uploadedBytes = remoteResult.metrics.encodedBytes,
                    downloadedBytes = remoteResult.metrics.responseBytes
                )
                val frameResult = FrameResult(
                    frameId = frameId,
                    timestampGeneration = timestampGeneration,
                    inferenceTime = endToEndMs,
                    displayTime = System.currentTimeMillis(),
                    imageReference = null,
                    processingSegment = ProcessingSegment.REMOTE,
                    detectionResult = remoteResult.detectionResult,
                    complexity = null,
                    inOrder = true,
                    metricsSnapshot = metricsAtCapture.copy(networkStatus = networkStatus),
                    remoteInference = remoteResult.metrics
                )
                detectionResult = remoteResult.detectionResult
                inferenceError = null
                modelReady = true
                lastInferenceTimeMillis = endToEndMs
                updateLiveFps(SystemClock.elapsedRealtime())
                frameResults.add(frameResult)
                acquisitionManager.addFrame(frameResult)
                frameCounter = frameResults.size
                if (frameCounter == 1 || frameCounter % LOG_EVERY_FRAMES == 0) {
                    Log.i(
                        TAG,
                        "frame=$frameId, segment=REMOTE, e2e=${endToEndMs}ms, " +
                            "rtt=${remoteResult.metrics.roundTripMs}ms, " +
                            "server=${remoteResult.metrics.totalServerMs}ms, " +
                            "inference=${remoteResult.metrics.inferenceMs}ms, " +
                            "uplink=${remoteResult.metrics.encodedBytes}B"
                    )
                }
            } catch (exception: Exception) {
                inferenceError = "Remote inference failed: ${exception.message ?: exception.javaClass.simpleName}"
                Log.e(TAG, "Remote inference failed", exception)
            } finally {
                remoteRequestInFlight.set(false)
            }
        }
    }

    private fun updateLiveFps(nowMillis: Long) {
        recentFrameTimes.addLast(nowMillis)
        while (recentFrameTimes.isNotEmpty() && nowMillis - recentFrameTimes.first > FPS_WINDOW_MILLIS) {
            recentFrameTimes.removeFirst()
        }
        processingFps = recentFrameTimes.size * 1000f / FPS_WINDOW_MILLIS
    }

    private fun warmUpModel(model: CVModel) {
        viewModelScope.launch {
            val warmUpFailure = withContext(Dispatchers.Default) {
                runCatching { frameProcessor.warmUp(model) }.exceptionOrNull()
            }
            modelReady = warmUpFailure == null
            inferenceError = warmUpFailure?.let {
                "Model initialization failed: ${it.message ?: it.javaClass.simpleName}"
            }
            if (warmUpFailure == null) {
                Log.i(TAG, "${model.name} LiteRT warm-up completed")
            } else {
                Log.e(TAG, "${model.name} LiteRT warm-up failed", warmUpFailure)
            }
        }
    }

    override fun onCleared() {
        frameProcessor.close()
        super.onCleared()
    }

    private companion object {
        const val FPS_WINDOW_MILLIS = 2_000L
        const val LOG_EVERY_FRAMES = 10
        const val TAG = "PPE-Inference"
    }
}
