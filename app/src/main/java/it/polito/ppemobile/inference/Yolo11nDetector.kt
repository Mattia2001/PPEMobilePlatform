package it.polito.ppemobile.inference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.InterpreterApi
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import it.polito.ppemobile.models.LocalExecutionInfo
import it.polito.ppemobile.models.enums.LocalAcceleration
import android.os.SystemClock
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class ModelBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float
) {
    val width: Float get() = (x2 - x1).coerceAtLeast(0f)
    val height: Float get() = (y2 - y1).coerceAtLeast(0f)
    val area: Float get() = width * height
    val centerX: Float get() = (x1 + x2) / 2f
    val centerY: Float get() = (y1 + y2) / 2f
}

internal data class ModelDetection(
    val classId: Int,
    val confidence: Float,
    val box: ModelBox
)

internal data class ModelFrameResult(
    val detections: List<ModelDetection>,
    val frameWidth: Int,
    val frameHeight: Int,
    val maxConfidenceByClass: FloatArray,
    val candidatesAboveThreshold: Int
)

/** Runs one compatible exported YOLO LiteRT artifact and returns class-aware NMS results. */
internal class YoloDetector(
    private val context: Context,
    private val modelDefinition: LocalModelDefinition,
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
    private val iouThreshold: Float = DEFAULT_IOU_THRESHOLD,
    private val acceleration: LocalAcceleration = LocalAcceleration.CPU
) : Closeable {

    // GPU creation, invocation and destruction must remain on the same thread.
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "PPE-LiteRT") }
    private var compiled: CompiledModel? = null
    private var compiledEnvironment: Environment? = null
    private var compiledInputs: List<TensorBuffer> = emptyList()
    private var compiledOutputs: List<TensorBuffer> = emptyList()
    private val compiledInput by lazy { FloatArray(inputFloatCount) }
    @Volatile var executionInfo: LocalExecutionInfo? = null
        private set
    private var interpreter: InterpreterApi? = null
    private val inputSize = modelDefinition.inputSize
    private val classCount = modelDefinition.classNames.size
    private val outputChannels = BOX_CHANNELS + classCount
    private val outputCandidates = modelDefinition.outputCandidates
    private val inputFloatCount = 3 * inputSize * inputSize
    private val inputBuffer = ByteBuffer
        .allocateDirect(inputFloatCount * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
    private val output = Array(1) {
        Array(outputChannels) { FloatArray(outputCandidates) }
    }
    private val pixels = IntArray(inputSize * inputSize)

    fun warmUp() = onWorker {
        ensureRuntime()
        inputBuffer.clear()
        repeat(inputFloatCount) { inputBuffer.putFloat(0.5f) }
        inputBuffer.rewind()
        runModel()
    }

    fun detect(imageProxy: ImageProxy): ModelFrameResult = onWorker {
        ensureRuntime()
        val rawBitmap = imageProxy.toBitmap()
        val orientedBitmap = rotateBitmap(rawBitmap, imageProxy.imageInfo.rotationDegrees)
        if (orientedBitmap !== rawBitmap) rawBitmap.recycle()

        val frameWidth = orientedBitmap.width
        val frameHeight = orientedBitmap.height
        val letterbox = createLetterbox(orientedBitmap)

        try {
            fillInputBuffer(letterbox.bitmap)
            runModel()
            val decoded = decodeOutput(letterbox, frameWidth, frameHeight)
            ModelFrameResult(
                detections = nonMaximumSuppression(decoded.detections),
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                maxConfidenceByClass = decoded.maxConfidenceByClass,
                candidatesAboveThreshold = decoded.detections.size
            )
        } finally {
            letterbox.bitmap.recycle()
            orientedBitmap.recycle()
        }
    }

    override fun close() {
        if (worker.isShutdown) return
        try {
            onWorker {
                closeCompiled()
                interpreter?.close()
                interpreter = null
            }
        } finally {
            worker.shutdown()
        }
    }

    private fun <T> onWorker(action: () -> T): T = try {
        worker.submit(Callable { action() }).get()
    } catch (failure: ExecutionException) {
        throw failure.cause ?: failure
    }

    private fun closeCompiled() {
        compiledInputs.forEach { it.close() }
        compiledOutputs.forEach { it.close() }
        compiledInputs = emptyList()
        compiledOutputs = emptyList()
        compiled?.close()
        compiled = null
        compiledEnvironment?.close()
        compiledEnvironment = null
    }

    private fun ensureRuntime() {
        if (executionInfo != null) return
        val hardware = LocalHardware.inspect(context)
        val failures = mutableListOf<String>()
        for (candidate in acceleration.candidates()) {
            val start = SystemClock.elapsedRealtimeNanos()
            try {
                inputBuffer.clear()
                repeat(inputFloatCount) { inputBuffer.putFloat(0.5f) }
                inputBuffer.rewind()
                if (candidate == "CPU") {
                    getOrCreateInterpreter().run(inputBuffer, output)
                } else {
                    check(candidate in hardware.runtimeAccelerators) {
                        "$candidate unavailable in installed LiteRT runtime (vendor libraries/driver may be missing)"
                    }
                    val options = CompiledModel.Options(Accelerator.valueOf(candidate))
                    compiled = if (candidate == "NPU") {
                        compiledEnvironment = Environment.create(
                            BuiltinNpuAcceleratorProvider(context.applicationContext)
                        )
                        CompiledModel.create(
                            context.assets,
                            modelDefinition.assetPath,
                            options,
                            compiledEnvironment!!
                        )
                    } else {
                        CompiledModel.create(context.assets, modelDefinition.assetPath, options)
                    }
                    compiledInputs = compiled!!.createInputBuffers()
                    compiledOutputs = compiled!!.createOutputBuffers()
                    check(compiledInputs.size == 1 && compiledOutputs.size == 1) { "Expected single model input/output" }
                    runCompiled()
                }
                check(output[0].all { channel -> channel.all { it.isFinite() } }) { "Non-finite warm-up output" }
                executionInfo = LocalExecutionInfo(
                    requested = acceleration.name, configuredBackend = candidate,
                    runtimeApi = if (candidate == "CPU") "Interpreter 2.1.5" else "CompiledModel 2.1.5",
                    fallbackReason = failures.takeIf { it.isNotEmpty() }?.joinToString("; "),
                    warmUpMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0,
                    soc = hardware.soc, gpuRenderer = hardware.gpuRenderer,
                    runtimeAccelerators = hardware.runtimeAccelerators,
                    acceleratorPlacementVerified = candidate == "CPU"
                )
                Log.i(TAG, "Local execution: $executionInfo")
                return
            } catch (failure: Exception) {
                closeCompiled()
                if (candidate == "CPU") throw failure
                failures += "$candidate: ${failure.message ?: failure.javaClass.simpleName}"
            } catch (failure: LinkageError) {
                closeCompiled()
                if (candidate == "CPU") throw failure
                failures += "$candidate: ${failure.message ?: failure.javaClass.simpleName}"
            }
        }
    }

    private fun runCompiled() {
        inputBuffer.rewind()
        inputBuffer.asFloatBuffer().get(compiledInput)
        compiledInputs[0].writeFloat(compiledInput)
        compiled!!.run(compiledInputs, compiledOutputs)
        val values = compiledOutputs[0].readFloat()
        check(values.size == outputChannels * outputCandidates) { "Unexpected compiled output size: ${values.size}" }
        for (channel in 0 until outputChannels) {
            values.copyInto(output[0][channel], 0, channel * outputCandidates, (channel + 1) * outputCandidates)
        }
    }

    private fun runModel() {
        if (compiled == null) {
            inputBuffer.rewind()
            getOrCreateInterpreter().run(inputBuffer, output)
            return
        }
        try {
            runCompiled()
        } catch (failure: Exception) {
            fallbackToCpu(failure)
        } catch (failure: LinkageError) {
            fallbackToCpu(failure)
        }
    }

    private fun fallbackToCpu(failure: Throwable) {
        closeCompiled()
        inputBuffer.rewind()
        getOrCreateInterpreter().run(inputBuffer, output)
        executionInfo = executionInfo?.copy(configuredBackend = "CPU", runtimeApi = "Interpreter 2.1.5",
            fallbackReason = listOfNotNull(executionInfo?.fallbackReason,
                "Invocation fallback: ${failure.message}").joinToString("; "), acceleratorPlacementVerified = true)
        Log.w(TAG, "Accelerated inference failed; CPU fallback", failure)
    }

    private fun getOrCreateInterpreter(): InterpreterApi {
        interpreter?.let { return it }

        val modelBytes = context.assets.open(modelDefinition.assetPath).use { it.readBytes() }
        val modelBuffer = ByteBuffer
            .allocateDirect(modelBytes.size)
            .order(ByteOrder.nativeOrder())
            .put(modelBytes)
        modelBuffer.rewind()

        val threadCount = min(4, max(1, java.lang.Runtime.getRuntime().availableProcessors()))
        val created = InterpreterApi.create(
            modelBuffer,
            InterpreterApi.Options().setNumThreads(threadCount)
        )

        try {
            check(created.inputTensorCount == 1) { "Expected one input tensor." }
            check(created.outputTensorCount == 1) { "Expected one output tensor." }
            check(created.getInputTensor(0).shape().contentEquals(intArrayOf(1, 3, inputSize, inputSize))) {
                "Unexpected model input: ${created.getInputTensor(0).shape().contentToString()}"
            }
            check(created.getOutputTensor(0).shape().contentEquals(
                intArrayOf(1, outputChannels, outputCandidates)
            )) {
                "Unexpected model output: ${created.getOutputTensor(0).shape().contentToString()}"
            }
        } catch (failure: Exception) {
            created.close()
            throw failure
        }

        Log.i(
            TAG,
            "LiteRT model loaded: model=${modelDefinition.model.name}, " +
                "dataset=${modelDefinition.datasetLabel}, " +
                "input=${created.getInputTensor(0).shape().contentToString()}, " +
                "output=${created.getOutputTensor(0).shape().contentToString()}, threads=$threadCount"
        )

        interpreter = created
        return created
    }

    private data class Letterbox(
        val bitmap: Bitmap,
        val scale: Float,
        val paddingX: Float,
        val paddingY: Float
    )

    private fun createLetterbox(source: Bitmap): Letterbox {
        val scale = min(inputSize / source.width.toFloat(), inputSize / source.height.toFloat())
        val scaledWidth = (source.width * scale).roundToInt().coerceIn(1, inputSize)
        val scaledHeight = (source.height * scale).roundToInt().coerceIn(1, inputSize)
        val paddingX = (inputSize - scaledWidth) / 2f
        val paddingY = (inputSize - scaledHeight) / 2f

        val destination = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(destination)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(
            source,
            null,
            RectF(
                paddingX,
                paddingY,
                paddingX + scaledWidth,
                paddingY + scaledHeight
            ),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        return Letterbox(destination, scale, paddingX, paddingY)
    }

    private fun fillInputBuffer(bitmap: Bitmap) {
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        inputBuffer.clear()

        // The exported model contract is NCHW: all R values, then G, then B.
        for (pixel in pixels) inputBuffer.putFloat(Color.red(pixel) / 255f)
        for (pixel in pixels) inputBuffer.putFloat(Color.green(pixel) / 255f)
        for (pixel in pixels) inputBuffer.putFloat(Color.blue(pixel) / 255f)
        inputBuffer.rewind()
    }

    private data class DecodedOutput(
        val detections: List<ModelDetection>,
        val maxConfidenceByClass: FloatArray
    )

    private fun decodeOutput(
        letterbox: Letterbox,
        frameWidth: Int,
        frameHeight: Int
    ): DecodedOutput {
        val detections = ArrayList<ModelDetection>()
        val maxConfidenceByClass = FloatArray(classCount)
        val prediction = output[0]

        for (candidateIndex in 0 until outputCandidates) {
            var bestClassId = -1
            var bestConfidence = 0f
            for (classId in 0 until classCount) {
                val score = prediction[BOX_CHANNELS + classId][candidateIndex]
                if (score.isFinite() && score > maxConfidenceByClass[classId]) {
                    maxConfidenceByClass[classId] = score
                }
                if (score > bestConfidence) {
                    bestConfidence = score
                    bestClassId = classId
                }
            }
            if (bestClassId < 0 || !bestConfidence.isFinite() || bestConfidence < confidenceThreshold) {
                continue
            }

            // Ultralytics LiteRT exports xywh coordinates normalized to the
            // model input. Convert them back to letterbox pixels
            // before removing padding and mapping to the source frame.
            val centerX = prediction[0][candidateIndex] * inputSize
            val centerY = prediction[1][candidateIndex] * inputSize
            val width = prediction[2][candidateIndex] * inputSize
            val height = prediction[3][candidateIndex] * inputSize
            if (!centerX.isFinite() || !centerY.isFinite() || !width.isFinite() || !height.isFinite()) {
                continue
            }

            val x1 = ((centerX - width / 2f - letterbox.paddingX) / letterbox.scale)
                .coerceIn(0f, frameWidth.toFloat())
            val y1 = ((centerY - height / 2f - letterbox.paddingY) / letterbox.scale)
                .coerceIn(0f, frameHeight.toFloat())
            val x2 = ((centerX + width / 2f - letterbox.paddingX) / letterbox.scale)
                .coerceIn(0f, frameWidth.toFloat())
            val y2 = ((centerY + height / 2f - letterbox.paddingY) / letterbox.scale)
                .coerceIn(0f, frameHeight.toFloat())
            if (x2 <= x1 || y2 <= y1) continue

            detections += ModelDetection(
                classId = bestClassId,
                confidence = bestConfidence,
                box = ModelBox(
                    x1 = x1 / frameWidth,
                    y1 = y1 / frameHeight,
                    x2 = x2 / frameWidth,
                    y2 = y2 / frameHeight
                )
            )
        }
        return DecodedOutput(
            detections = detections,
            maxConfidenceByClass = maxConfidenceByClass
        )
    }

    private fun nonMaximumSuppression(candidates: List<ModelDetection>): List<ModelDetection> {
        val selected = ArrayList<ModelDetection>()
        for (candidate in candidates.sortedByDescending { it.confidence }) {
            val suppressed = selected.any { kept ->
                kept.classId == candidate.classId && intersectionOverUnion(kept.box, candidate.box) > iouThreshold
            }
            if (!suppressed) selected += candidate
            if (selected.size >= MAX_DETECTIONS) break
        }
        return selected
    }

    private fun intersectionOverUnion(first: ModelBox, second: ModelBox): Float {
        val intersectionWidth = (min(first.x2, second.x2) - max(first.x1, second.x1)).coerceAtLeast(0f)
        val intersectionHeight = (min(first.y2, second.y2) - max(first.y1, second.y1)).coerceAtLeast(0f)
        val intersection = intersectionWidth * intersectionHeight
        val union = first.area + second.area - intersection
        return if (union > 0f) intersection / union else 0f
    }

    private fun rotateBitmap(source: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees % 360 == 0) return source
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private companion object {
        const val BOX_CHANNELS = 4
        const val MAX_DETECTIONS = 100
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.25f
        const val DEFAULT_IOU_THRESHOLD = 0.45f
        const val TAG = "PPE-YOLO"
    }
}
