package it.polito.ppemobile.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.polito.ppemobile.inference.FrameProcessor
import it.polito.ppemobile.inference.LocalModelCatalog
import it.polito.ppemobile.remote.RemoteInferenceClient
import it.polito.ppemobile.storage.AppSettingsStore
import it.polito.ppemobile.storage.AcquisitionConfigStore
import it.polito.ppemobile.inference.LocalHardware
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SystemCheckState {
    CHECKING,
    READY,
    WARNING,
    UNAVAILABLE
}

data class SystemCheck(
    val label: String,
    val value: String,
    val detail: String,
    val state: SystemCheckState
)

data class SystemStatusUiState(
    val device: SystemCheck = checking("Device"),
    val camera: SystemCheck = checking("Camera"),
    val runtime: SystemCheck = checking("Runtime"),
    val hardware: SystemCheck = checking("Hardware"),
    val server: SystemCheck = checking("Server"),
    val refreshing: Boolean = true
) {
    companion object {
        private fun checking(label: String) = SystemCheck(
            label = label,
            value = "Checking",
            detail = "",
            state = SystemCheckState.CHECKING
        )
    }
}

class SystemStatusViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val appSettingsStore = AppSettingsStore(appContext)
    private val _uiState = MutableStateFlow(SystemStatusUiState())
    val uiState: StateFlow<SystemStatusUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.refreshing && _uiState.value.device.state != SystemCheckState.CHECKING) return

        _uiState.value = SystemStatusUiState()
        viewModelScope.launch {
            val checks = listOf(
                async { checkDevice() },
                async { checkCamera() },
                async { checkRuntime() },
                async { checkServer() }
            ).awaitAll()

            _uiState.value = SystemStatusUiState(
                device = checks[0],
                camera = checks[1],
                runtime = checks[2],
                hardware = withContext(Dispatchers.Default) {
                    val hardware = LocalHardware.inspect(appContext)
                    SystemCheck("Hardware", hardware.soc,
                        "CPU: ${hardware.cpuCores} cores · GPU: ${hardware.gpuRenderer} · " +
                            "LiteRT available: ${hardware.runtimeAccelerators.joinToString()}" +
                            if ("NPU" !in hardware.runtimeAccelerators) " · NPU runtime unavailable (physical NPU presence not determined)" else " · Model compatibility requires warm-up",
                        if (hardware.probeError == null) SystemCheckState.READY else SystemCheckState.WARNING)
                },
                server = checks[3],
                refreshing = false
            )
        }
    }

    private suspend fun checkDevice(): SystemCheck = withContext(Dispatchers.IO) {
        val supportedAbi = Build.SUPPORTED_ABIS.any { it in SUPPORTED_ABIS }
        val freeMegabytes = appContext.filesDir.usableSpace / BYTES_PER_MEGABYTE
        val enoughStorage = freeMegabytes >= MIN_FREE_STORAGE_MB
        val ready = supportedAbi && enoughStorage
        val detail = buildString {
            append(Build.MODEL)
            append(" · Android ")
            append(Build.VERSION.RELEASE)
            append(" · ")
            append(String.format(Locale.US, "%,d MB free", freeMegabytes))
        }

        SystemCheck(
            label = "Device",
            value = if (ready) "Ready" else "Not ready",
            detail = detail,
            state = if (ready) SystemCheckState.READY else SystemCheckState.UNAVAILABLE
        )
    }

    private suspend fun checkCamera(): SystemCheck = withContext(Dispatchers.Default) {
        val packageManager = appContext.packageManager
        val available = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
        val permissionGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        when {
            !available -> SystemCheck(
                label = "Camera",
                value = "Unavailable",
                detail = "No camera hardware reported by Android",
                state = SystemCheckState.UNAVAILABLE
            )

            !permissionGranted -> SystemCheck(
                label = "Camera",
                value = "Permission required",
                detail = "Camera hardware detected",
                state = SystemCheckState.WARNING
            )

            else -> SystemCheck(
                label = "Camera",
                value = "Available",
                detail = "Hardware detected · permission granted",
                state = SystemCheckState.READY
            )
        }
    }

    private suspend fun checkRuntime(): SystemCheck = withContext(Dispatchers.Default) {
        val configuration = AcquisitionConfigStore(appContext).load()
        var execution: it.polito.ppemobile.models.LocalExecutionInfo? = null
        val failure = runCatching {
            FrameProcessor(appContext).use { processor ->
                processor.selectModel(configuration.cvModel, configuration.localAcceleration)
                processor.warmUp(configuration.cvModel)
                execution = processor.executionInfo()
            }
        }.exceptionOrNull()

        SystemCheck(
            label = "Runtime",
            value = if (failure == null) "Ready" else "Error",
            detail = if (failure == null) {
                "${configuration.cvModel.name} · ${execution?.configuredBackend} configured · warm-up passed" +
                    (if (execution?.acceleratorPlacementVerified == false) " · operator placement unverified" else "") +
                    (execution?.fallbackReason?.let { " · $it" } ?: "")
            } else {
                failure.message ?: failure.javaClass.simpleName
            },
            state = if (failure == null) SystemCheckState.READY else SystemCheckState.UNAVAILABLE
        )
    }

    private suspend fun checkServer(): SystemCheck = withContext(Dispatchers.IO) {
        val settings = appSettingsStore.loadRemoteServer()
        val connectivityManager = appContext.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager
        if (connectivityManager.activeNetwork == null) {
            return@withContext SystemCheck(
                label = "Server",
                value = "No network",
                detail = settings.baseUrl,
                state = SystemCheckState.WARNING
            )
        }

        runCatching {
            RemoteInferenceClient(settings.baseUrl).status()
        }.fold(
            onSuccess = { status ->
                val gpu = status.gpuName ?: status.device
                SystemCheck(
                    label = "Server",
                    value = if (status.ready) "Connected" else "Not ready",
                    detail = "${status.modelId} · $gpu · ${"%.0f".format(status.roundTripMs)} ms",
                    state = if (status.ready) SystemCheckState.READY else SystemCheckState.WARNING
                )
            },
            onFailure = { failure ->
                SystemCheck(
                    label = "Server",
                    value = "Not connected",
                    detail = "${settings.baseUrl} · ${failure.message ?: "enable VPN"}",
                    state = SystemCheckState.WARNING
                )
            }
        )
    }

    private companion object {
        const val MIN_FREE_STORAGE_MB = 100L
        const val BYTES_PER_MEGABYTE = 1024L * 1024L
        val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
    }
}
