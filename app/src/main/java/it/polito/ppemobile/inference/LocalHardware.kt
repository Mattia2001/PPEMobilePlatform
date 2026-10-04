package it.polito.ppemobile.inference

import android.content.Context
import android.os.Build
import android.opengl.EGL14
import android.opengl.GLES20
import com.google.ai.edge.litert.BuiltinNpuAcceleratorProvider
import com.google.ai.edge.litert.Environment

data class LocalHardwareInfo(
    val soc: String,
    val cpuCores: Int,
    val gpuRenderer: String,
    val runtimeAccelerators: List<String>,
    val probeError: String?
)

/** Runtime availability is separate from physical silicon and model compatibility. */
object LocalHardware {
    @Volatile private var cached: LocalHardwareInfo? = null

    @Synchronized fun inspect(context: Context): LocalHardwareInfo {
        cached?.let { return it }
        var error: String? = null
        val accelerators = try {
            val detected = Environment.create().use { env ->
                env.getAvailableAccelerators().map { it.name }.toMutableList()
            }
            val npuProvider = BuiltinNpuAcceleratorProvider(context.applicationContext)
            if (npuProvider.isDeviceSupported() && npuProvider.isLibraryReady()) {
                Environment.create(npuProvider).use { env ->
                    detected += env.getAvailableAccelerators().map { it.name }
                }
            }
            detected.distinct()
        } catch (failure: Exception) {
            error = failure.message
            listOf("CPU")
        } catch (failure: LinkageError) {
            error = failure.message
            listOf("CPU")
        }
        return LocalHardwareInfo(
            soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE,
            cpuCores = java.lang.Runtime.getRuntime().availableProcessors(),
            gpuRenderer = readGpuRenderer(),
            runtimeAccelerators = accelerators,
            probeError = error
        ).also { cached = it }
    }

    private fun readGpuRenderer(): String {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return "Not reported"
        val versions = IntArray(2)
        if (!EGL14.eglInitialize(display, versions, 0, versions, 1)) return "Not reported"
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        try {
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE
            ), 0, configs, 0, 1, count, 0) && count[0] > 0)
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
            surface = EGL14.eglCreatePbufferSurface(display, configs[0],
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
            check(EGL14.eglMakeCurrent(display, surface, surface, context))
            return GLES20.glGetString(GLES20.GL_RENDERER) ?: "Not reported"
        } catch (_: Exception) {
            return "Not reported"
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
            EGL14.eglReleaseThread()
        }
    }
}
