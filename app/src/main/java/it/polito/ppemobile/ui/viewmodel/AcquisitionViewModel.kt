package it.polito.ppemobile.ui.viewmodel

import android.app.Application
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.polito.ppemobile.domain.AcquisitionManager
import it.polito.ppemobile.inference.FrameProcessor
import it.polito.ppemobile.metrics.DeviceMetricsSampler
import it.polito.ppemobile.models.*
import it.polito.ppemobile.models.enums.AcquisitionState
import it.polito.ppemobile.models.enums.ProcessingSegment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

class AcquisitionViewModel(application: Application) : AndroidViewModel(application) {

    private val metricsSampler = DeviceMetricsSampler(application.applicationContext)
    private val frameProcessor = FrameProcessor()
    private val acquisitionManager = AcquisitionManager()

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

        detectionResult = null
        currentAcquisition = null
        frameResults.clear()
        frameCounter = 0

        acquisitionManager.start(configuration)
        acquisitionState = AcquisitionState.RUNNING
    }

    fun stopAcquisition() {
        acquisitionState = AcquisitionState.STOPPED
        currentAcquisition = acquisitionManager.stop()
        detectionResult = null
    }

    fun processFrame(imageProxy: ImageProxy) {
        if (acquisitionState != AcquisitionState.RUNNING) return

        val frameId = "frame_${frameCounter.toString().padStart(6, '0')}"
        val timestampGeneration = System.currentTimeMillis()

        lateinit var result: DetectionResult

        val inferenceTime = measureTimeMillis {
            result = frameProcessor.processFrame(imageProxy)
        }

        detectionResult = result

        val frameResult = FrameResult(
            frameId = frameId,
            timestampGeneration = timestampGeneration,
            inferenceTime = inferenceTime,
            displayTime = System.currentTimeMillis(),
            imageReference = null,
            processingSegment = ProcessingSegment.LOCAL,
            detectionResult = result,
            complexity = null,
            inOrder = true,
            metricsSnapshot = metrics ?: metricsSampler.sample()
        )

        frameResults.add(frameResult)
        acquisitionManager.addFrame(frameResult)
        frameCounter++
    }
}