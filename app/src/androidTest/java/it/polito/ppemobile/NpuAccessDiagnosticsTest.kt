package it.polito.ppemobile

import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
import it.polito.ppemobile.inference.LocalModelCatalog
import it.polito.ppemobile.inference.YoloDetector
import it.polito.ppemobile.models.enums.CVModel
import it.polito.ppemobile.models.enums.LocalAcceleration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.tensorflow.lite.InterpreterApi

/** Manual physical-device diagnostic. No camera, root, or undocumented vendor calls. */
@RunWith(AndroidJUnit4::class)
class NpuAccessDiagnosticsTest {
    interface NnApi : Library {
        fun ANeuralNetworks_getDeviceCount(count: IntByReference): Int
        fun ANeuralNetworks_getDevice(index: Int, device: PointerByReference): Int
        fun ANeuralNetworksDevice_getName(device: Pointer, name: PointerByReference): Int
        fun ANeuralNetworksDevice_getVersion(device: Pointer, version: PointerByReference): Int
        fun ANeuralNetworksDevice_getType(device: Pointer, type: IntByReference): Int
        fun ANeuralNetworksDevice_getFeatureLevel(device: Pointer, level: LongByReference): Int
        fun ANeuralNetworksModel_create(model: PointerByReference): Int
        fun ANeuralNetworksModel_addOperand(model: Pointer, operand: Operand): Int
        fun ANeuralNetworksModel_setOperandValue(model: Pointer, index: Int, value: Pointer, length: Long): Int
        fun ANeuralNetworksModel_addOperation(model: Pointer, type: Int, nIn: Int, inputs: IntArray, nOut: Int, outputs: IntArray): Int
        fun ANeuralNetworksModel_identifyInputsAndOutputs(model: Pointer, nIn: Int, inputs: IntArray, nOut: Int, outputs: IntArray): Int
        fun ANeuralNetworksModel_finish(model: Pointer): Int
        fun ANeuralNetworksModel_getSupportedOperationsForDevices(model: Pointer, devices: Array<Pointer>, count: Int, supported: ByteArray): Int
        fun ANeuralNetworksCompilation_createForDevices(model: Pointer, devices: Array<Pointer>, count: Int, compilation: PointerByReference): Int
        fun ANeuralNetworksCompilation_finish(compilation: Pointer): Int
        fun ANeuralNetworksExecution_create(compilation: Pointer, execution: PointerByReference): Int
        fun ANeuralNetworksExecution_setInput(execution: Pointer, index: Int, type: Pointer?, data: Pointer, length: Long): Int
        fun ANeuralNetworksExecution_setOutput(execution: Pointer, index: Int, type: Pointer?, data: Pointer, length: Long): Int
        fun ANeuralNetworksExecution_compute(execution: Pointer): Int
        fun ANeuralNetworksExecution_free(execution: Pointer)
        fun ANeuralNetworksCompilation_free(compilation: Pointer)
        fun ANeuralNetworksModel_free(model: Pointer)
    }

    @Structure.FieldOrder("type", "dimensionCount", "dimensions", "scale", "zeroPoint")
    class Operand : Structure() {
        @JvmField var type = 0
        @JvmField var dimensionCount = 0
        @JvmField var dimensions: Pointer? = null
        @JvmField var scale = 0f
        @JvmField var zeroPoint = 0
    }

    private fun checkCode(code: Int, operation: String) = check(code == 0) { "$operation: NNAPI status $code" }

    @Test fun probeApplicationAccess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = JSONObject().put("fingerprint", Build.FINGERPRINT)
            .put("sdk", Build.VERSION.SDK_INT).put("soc", Build.SOC_MODEL)
            .put("applicationUid", android.os.Process.myUid())
            .put("nativePointerBytes", Native.POINTER_SIZE)
        val libraries = JSONArray()
        for (library in listOf(
            "LiteRtCompilerPlugin_MediaTek", "LiteRtDispatch_MediaTek",
            "neuron_runtime", "neuron_runtime.7", "neuron_graph_delegate.mtk",
            "neuronusdk_adapter.mtk", "tflite_mtk.mtk"
        )) {
            val entry = JSONObject().put("library", "lib$library.so")
            try {
                System.loadLibrary(library)
                entry.put("loaded", true)
            } catch (failure: LinkageError) {
                entry.put("loaded", false).put("error", failure.toString())
            }
            libraries.put(entry)
        }
        report.put("libraryLoads", libraries)
        val liteRt = JSONObject()
        report.put("liteRt", liteRt)
        try {
            Environment.create().use { env ->
                liteRt.put("availableAccelerators", JSONArray(env.getAvailableAccelerators().map { it.name }))
            }
            val provider = BuiltinNpuAcceleratorProvider(context)
            liteRt.put("builtinProviderDeviceSupported", provider.isDeviceSupported())
            liteRt.put("builtinProviderLibraryReady", provider.isLibraryReady())
            // In 2.1.5 isLibraryReady() returns true unconditionally; registration
            // must be tested independently instead of treating it as proof.
            Environment.create(provider).use { env ->
                liteRt.put("providerEnvironmentAccelerators", JSONArray(env.getAvailableAccelerators().map { it.name }))
                val minimal = JSONObject().put("requested", "NPU").put("placementVerified", false)
                liteRt.put("minimalAdd10x10", minimal)
                try {
                    val assetPath = "diagnostics/add_10x10.tflite"
                    val modelBytes = context.assets.open(assetPath).use { it.readBytes() }
                    val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size)
                        .order(ByteOrder.nativeOrder()).put(modelBytes).apply { rewind() }
                    val inputSizes = InterpreterApi.create(modelBuffer, InterpreterApi.Options()).use { interpreter ->
                        (0 until interpreter.inputTensorCount).map { tensorIndex ->
                            interpreter.getInputTensor(tensorIndex).shape().fold(1) { size, dimension -> size * dimension }
                        }
                    }
                    minimal.put("inputElements", JSONArray(inputSizes))
                    CompiledModel.create(context.assets, assetPath,
                        CompiledModel.Options(Accelerator.NPU), env).use { model ->
                        minimal.put("compiled", true)
                        val inputs = model.createInputBuffers()
                        try {
                            val outputs = model.createOutputBuffers()
                            try {
                                check(inputs.size == inputSizes.size) { "Unexpected minimal model input count" }
                                inputs.forEachIndexed { index, buffer ->
                                    buffer.writeFloat(FloatArray(inputSizes[index]) { (index + 1).toFloat() })
                                }
                                val begin = SystemClock.elapsedRealtimeNanos()
                                model.run(inputs, outputs)
                                minimal.put("singleRunMs", (SystemClock.elapsedRealtimeNanos() - begin) / 1e6)
                                val values = outputs.single().readFloat()
                                minimal.put("outputElements", values.size)
                                minimal.put("outputFinite", values.isNotEmpty() && values.all { it.isFinite() })
                            } finally { outputs.forEach { it.close() } }
                        } finally { inputs.forEach { it.close() } }
                    }
                } catch (failure: Exception) {
                    minimal.put("error", failure.toString())
                } catch (failure: LinkageError) {
                    minimal.put("error", failure.toString())
                }
                val yolo = JSONObject().put("requested", "NPU").put("placementVerified", false)
                liteRt.put("yolo26n640", yolo)
                try {
                    CompiledModel.create(context.assets,
                        "models/yolo26n_sh17_pictor_construction_v2_best_fp32.tflite",
                        CompiledModel.Options(Accelerator.NPU), env).use { model ->
                        yolo.put("compiled", true)
                        val inputs = model.createInputBuffers()
                        try {
                            val outputs = model.createOutputBuffers()
                            try {
                                inputs.single().writeFloat(FloatArray(3 * 640 * 640) { 0.5f })
                                val begin = SystemClock.elapsedRealtimeNanos()
                                model.run(inputs, outputs)
                                yolo.put("singleSyntheticRunMs", (SystemClock.elapsedRealtimeNanos() - begin) / 1e6)
                                val values = outputs.single().readFloat()
                                yolo.put("outputElements", values.size)
                                yolo.put("outputFinite", values.all { it.isFinite() })
                            } finally { outputs.forEach { it.close() } }
                        } finally { inputs.forEach { it.close() } }
                    }
                } catch (failure: Exception) {
                    yolo.put("error", failure.toString())
                } catch (failure: LinkageError) {
                    yolo.put("error", failure.toString())
                }
            }
        } catch (failure: Exception) {
            liteRt.put("error", failure.toString())
        } catch (failure: LinkageError) {
            liteRt.put("error", failure.toString())
        }
        val applicationBackend = JSONObject()
        report.put("applicationBackend", applicationBackend)
        try {
            YoloDetector(
                context = context,
                modelDefinition = LocalModelCatalog.require(CVModel.YOLO26N_V2),
                acceleration = LocalAcceleration.NPU
            ).use { detector ->
                detector.warmUp()
                val execution = checkNotNull(detector.executionInfo)
                applicationBackend
                    .put("requested", execution.requested)
                    .put("configuredBackend", execution.configuredBackend)
                    .put("runtimeApi", execution.runtimeApi)
                    .put("fallbackReason", execution.fallbackReason ?: JSONObject.NULL)
                    .put("warmUpMs", execution.warmUpMs)
                    .put("runtimeAccelerators", JSONArray(execution.runtimeAccelerators))
            }
        } catch (failure: Exception) {
            applicationBackend.put("error", failure.toString())
        } catch (failure: LinkageError) {
            applicationBackend.put("error", failure.toString())
        }
        try {
            check(Native.POINTER_SIZE == 8) { "Diagnostic size_t bindings require arm64" }
            val api = Native.load("neuralnetworks", NnApi::class.java)
            val count = IntByReference()
            checkCode(api.ANeuralNetworks_getDeviceCount(count), "getDeviceCount")
            val devices = JSONArray()
            report.put("nnapiDevices", devices)
            repeat(count.value) { index ->
                val device = PointerByReference()
                checkCode(api.ANeuralNetworks_getDevice(index, device), "getDevice")
                val name = PointerByReference()
                val version = PointerByReference()
                val type = IntByReference()
                val level = LongByReference()
                checkCode(api.ANeuralNetworksDevice_getName(device.value, name), "getName")
                checkCode(api.ANeuralNetworksDevice_getVersion(device.value, version), "getVersion")
                checkCode(api.ANeuralNetworksDevice_getType(device.value, type), "getType")
                checkCode(api.ANeuralNetworksDevice_getFeatureLevel(device.value, level), "getFeatureLevel")
                val item = JSONObject().put("name", name.value.getString(0))
                    .put("version", version.value.getString(0)).put("type", type.value)
                    .put("featureLevel", level.value)
                devices.put(item)
                // Single explicitly selected driver, with NNAPI CPU fallback disabled by createForDevices.
                item.put("addFloat32", runAdd(api, device.value, false))
                item.put("addQuant8", runAdd(api, device.value, true))
            }
        } catch (failure: Exception) {
            report.put("nnapiError", failure.toString())
        } catch (failure: LinkageError) {
            report.put("nnapiError", failure.toString())
        }
        File(context.filesDir, "npu-diagnostic.json").writeText(report.toString(2))
        Log.i("PPE-NPU-Probe", report.toString())
    }

    private fun runAdd(api: NnApi, device: Pointer, quantized: Boolean): JSONObject {
        val result = JSONObject().put("explicitDeviceOnly", true)
        val model = PointerByReference()
        val compilation = PointerByReference()
        val execution = PointerByReference()
        try {
            checkCode(api.ANeuralNetworksModel_create(model), "modelCreate")
            Memory(16).use { dimensions ->
                dimensions.write(0, intArrayOf(1, 4, 4, 4), 0, 4)
                val tensor = Operand().apply {
                    type = if (quantized) 5 else 3 // TENSOR_QUANT8_ASYMM / TENSOR_FLOAT32
                    dimensionCount = 4
                    this.dimensions = dimensions
                    scale = if (quantized) 0.5f else 0f
                    zeroPoint = if (quantized) 128 else 0
                }
                checkCode(api.ANeuralNetworksModel_addOperand(model.value, tensor), "operand0")
                checkCode(api.ANeuralNetworksModel_addOperand(model.value, tensor), "operand1")
                checkCode(api.ANeuralNetworksModel_addOperand(model.value, Operand().apply { type = 1 }), "activationOperand")
                checkCode(api.ANeuralNetworksModel_addOperand(model.value, tensor), "outputOperand")
            }
            Memory(4).use { activation ->
                activation.setInt(0, 0) // FUSED_NONE
                checkCode(api.ANeuralNetworksModel_setOperandValue(model.value, 2, activation, 4), "setActivation")
            }
            checkCode(api.ANeuralNetworksModel_addOperation(model.value, 0, 3, intArrayOf(0, 1, 2), 1, intArrayOf(3)), "addOperation")
            checkCode(api.ANeuralNetworksModel_identifyInputsAndOutputs(model.value, 2, intArrayOf(0, 1), 1, intArrayOf(3)), "identifyIO")
            checkCode(api.ANeuralNetworksModel_finish(model.value), "modelFinish")
            val supported = ByteArray(1)
            checkCode(api.ANeuralNetworksModel_getSupportedOperationsForDevices(model.value, arrayOf(device), 1, supported), "supportedOps")
            result.put("supported", supported[0].toInt() != 0)
            if (supported[0].toInt() == 0) return result
            val start = SystemClock.elapsedRealtimeNanos()
            checkCode(api.ANeuralNetworksCompilation_createForDevices(model.value, arrayOf(device), 1, compilation), "compileForDevice")
            checkCode(api.ANeuralNetworksCompilation_finish(compilation.value), "compileFinish")
            result.put("compileMs", (SystemClock.elapsedRealtimeNanos() - start) / 1e6)
            checkCode(api.ANeuralNetworksExecution_create(compilation.value, execution), "executionCreate")
            val bytes = if (quantized) 64L else 256L
            Memory(bytes).use { a -> Memory(bytes).use { b -> Memory(bytes).use { output ->
                repeat(64) { i ->
                    if (quantized) { a.setByte(i.toLong(), 130.toByte()); b.setByte(i.toLong(), 132.toByte()) }
                    else { a.setFloat(i * 4L, 1f); b.setFloat(i * 4L, 2f) }
                }
                output.clear()
                checkCode(api.ANeuralNetworksExecution_setInput(execution.value, 0, null, a, bytes), "input0")
                checkCode(api.ANeuralNetworksExecution_setInput(execution.value, 1, null, b, bytes), "input1")
                checkCode(api.ANeuralNetworksExecution_setOutput(execution.value, 0, null, output, bytes), "output")
                val begin = SystemClock.elapsedRealtimeNanos()
                checkCode(api.ANeuralNetworksExecution_compute(execution.value), "compute")
                result.put("computeMs", (SystemClock.elapsedRealtimeNanos() - begin) / 1e6)
                val correct = (0 until 64).all { i ->
                    if (quantized) (output.getByte(i.toLong()).toInt() and 255) == 134
                    else abs(output.getFloat(i * 4L) - 3f) < 0.001f
                }
                result.put("outputCorrect", correct)
            } } }
        } catch (failure: Exception) {
            result.put("error", failure.toString())
        } finally {
            execution.value?.let { api.ANeuralNetworksExecution_free(it) }
            compilation.value?.let { api.ANeuralNetworksCompilation_free(it) }
            model.value?.let { api.ANeuralNetworksModel_free(it) }
        }
        return result
    }
}
