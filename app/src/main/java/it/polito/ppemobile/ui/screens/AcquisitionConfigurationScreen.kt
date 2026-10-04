package it.polito.ppemobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.polito.ppemobile.models.AcquisitionConfig
import it.polito.ppemobile.inference.LocalModelCatalog
import it.polito.ppemobile.models.enums.CVModel
import it.polito.ppemobile.models.enums.OffloadingStrategy
import it.polito.ppemobile.models.enums.Runtime
import it.polito.ppemobile.models.enums.LocalAcceleration
import it.polito.ppemobile.ui.formatters.displayName
import it.polito.ppemobile.ui.formatters.supportingText
import it.polito.ppemobile.ui.components.ModelMetricsDialog
import it.polito.ppemobile.ui.viewmodel.SystemCheckState
import it.polito.ppemobile.ui.viewmodel.SystemStatusViewModel

@Composable
fun AcquisitionConfigurationScreen(
    initialConfiguration: AcquisitionConfig,
    onStartAcquisitionClick: (AcquisitionConfig) -> Unit,
    modifier: Modifier = Modifier,
    systemStatusViewModel: SystemStatusViewModel = viewModel()
) {
    val systemStatus by systemStatusViewModel.uiState.collectAsState()
    val serverReady = systemStatus.server.state == SystemCheckState.READY
    var selectedModel by remember(initialConfiguration) {
        mutableStateOf(initialConfiguration.cvModel)
    }
    var selectedRuntime by remember(initialConfiguration) {
        mutableStateOf(initialConfiguration.runtime)
    }
    var selectedAcceleration by remember(initialConfiguration) { mutableStateOf(initialConfiguration.localAcceleration) }
    var selectedStrategy by remember(initialConfiguration) {
        mutableStateOf(initialConfiguration.offloadingStrategy)
    }
    var selectedQuality by remember(initialConfiguration) {
        mutableStateOf(initialConfiguration.videoQuality)
    }
    var selectedFps by remember(initialConfiguration) {
        mutableIntStateOf(initialConfiguration.fps)
    }
    var selectedCompression by remember(initialConfiguration) {
        mutableIntStateOf(initialConfiguration.compressionLevel)
    }
    var metricsModel by remember { mutableStateOf<CVModel?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Acquisition Configuration",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                ConfigDropdown(
                    label = "Model",
                    selectedLabel = selectedModel.displayName(),
                    options = LocalModelCatalog.availableModels.map { model ->
                        ConfigOption(model, model.displayName())
                    },
                    supportingText = selectedModel.supportingText(),
                    onInfoClick = { metricsModel = selectedModel },
                    onValueSelected = { selectedModel = it }
                )

                ConfigDropdown(
                    label = "Runtime",
                    selectedLabel = selectedRuntime.displayName(),
                    options = listOf(
                        ConfigOption(Runtime.TFLITE, "TensorFlow Lite"),
                        ConfigOption(
                            Runtime.REMOTE_SERVER,
                            "Remote Server",
                            serverReady,
                            if (serverReady) null else "Server unavailable; check VPN and Settings"
                        ),
                        ConfigOption(
                            Runtime.ONNX_RUNTIME,
                            "ONNX Runtime",
                            false,
                            "No ONNX model/runtime integrated"
                        )
                    ),
                    supportingText = "The runtime loads and executes the model on the device. This build uses LiteRT for the .tflite model.",
                    onValueSelected = { runtime ->
                        selectedRuntime = runtime
                        if (runtime == Runtime.REMOTE_SERVER) {
                            selectedStrategy = OffloadingStrategy.ALWAYS_OFFLOAD
                            selectedModel = CVModel.YOLO26N_V2
                        }
                    }
                )

                ConfigDropdown(
                    label = "Local accelerator",
                    selectedLabel = selectedAcceleration.label,
                    options = LocalAcceleration.entries.map { ConfigOption(it, it.label) },
                    fieldEnabled = selectedRuntime == Runtime.TFLITE,
                    supportingText = "CPU preserves the baseline. Auto tries NPU, GPU, then CPU; warm-up checks model compatibility. NPU needs vendor runtime libraries. Fallback is recorded in JSONL.",
                    onValueSelected = { selectedAcceleration = it }
                )

                ConfigDropdown(
                    label = "Offloading Strategy",
                    selectedLabel = selectedStrategy.displayName(),
                    options = listOf(
                        ConfigOption(OffloadingStrategy.ALWAYS_LOCAL, "Always Local"),
                        ConfigOption(
                            OffloadingStrategy.ALWAYS_OFFLOAD,
                            "Always Offload",
                            serverReady,
                            if (serverReady) null else "Remote inference service not reachable"
                        ),
                        ConfigOption(
                            OffloadingStrategy.GREEDY,
                            "Greedy",
                            false,
                            "Planned strategy"
                        ),
                        ConfigOption(
                            OffloadingStrategy.REINFORCEMENT_LEARNING,
                            "Reinforcement Learning",
                            false,
                            "Planned strategy"
                        )
                    ),
                    supportingText = if (serverReady) {
                        systemStatus.server.detail
                    } else {
                        "Activate the VPN, verify the endpoint in Settings, then reopen this screen."
                    },
                    onValueSelected = { strategy ->
                        selectedStrategy = strategy
                        if (strategy == OffloadingStrategy.ALWAYS_OFFLOAD) {
                            selectedRuntime = Runtime.REMOTE_SERVER
                            selectedModel = CVModel.YOLO26N_V2
                        } else if (strategy == OffloadingStrategy.ALWAYS_LOCAL) {
                            selectedRuntime = Runtime.TFLITE
                        }
                    }
                )

                ConfigDropdown(
                    label = "Analysis Resolution",
                    selectedLabel = selectedQuality,
                    options = listOf(
                        ConfigOption("720p", "720p"),
                        ConfigOption("1080p", "1080p"),
                        ConfigOption("4K", "4K", false, "Disabled for the current FP32 baseline")
                    ),
                    supportingText = "Requested from CameraX; the closest supported resolution may be selected by the device.",
                    onValueSelected = { selectedQuality = it }
                )

                ConfigDropdown(
                    label = "Maximum Analysis FPS",
                    selectedLabel = "$selectedFps FPS",
                    options = listOf(
                        ConfigOption(15, "15 FPS"),
                        ConfigOption(30, "30 FPS"),
                        ConfigOption(60, "60 FPS")
                    ),
                    supportingText = "Upper bound only; actual FPS is limited by inference latency.",
                    onValueSelected = { selectedFps = it }
                )

                ConfigDropdown<Int>(
                    label = "JPEG Compression",
                    selectedLabel = "JPEG $selectedCompression",
                    options = listOf(60, 70, 80, 90).map { ConfigOption(it, "JPEG $it") },
                    supportingText = if (selectedStrategy == OffloadingStrategy.ALWAYS_OFFLOAD) {
                        "Controls uplink payload size and image quality for remote inference."
                    } else {
                        "Not applied to local inference."
                    },
                    onValueSelected = { selectedCompression = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                onStartAcquisitionClick(
                    initialConfiguration.copy(
                        offloadingStrategy = selectedStrategy,
                        cvModel = selectedModel,
                        runtime = selectedRuntime,
                        localAcceleration = selectedAcceleration,
                        fps = selectedFps,
                        videoQuality = selectedQuality,
                        compressionLevel = selectedCompression
                    )
                )
            },
            enabled = selectedStrategy != OffloadingStrategy.ALWAYS_OFFLOAD || serverReady,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Acquisition")
        }
    }

    metricsModel?.let { model ->
        ModelMetricsDialog(
            model = model,
            onDismissRequest = { metricsModel = null }
        )
    }
}

private data class ConfigOption<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true,
    val unavailableReason: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ConfigDropdown(
    label: String,
    selectedLabel: String,
    options: List<ConfigOption<T>>,
    onValueSelected: (T) -> Unit,
    supportingText: String? = null,
    fieldEnabled: Boolean = true,
    onInfoClick: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            onInfoClick?.let { showInfo ->
                IconButton(
                    onClick = showInfo,
                    modifier = Modifier.semantics {
                        contentDescription = "$label information"
                    }
                ) {
                    Text(
                        text = "ⓘ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = {
                if (fieldEnabled) expanded = !expanded
            }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                enabled = fieldEnabled,
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = fieldEnabled
                    )
                    .fillMaxWidth(),
                trailingIcon = {
                    if (fieldEnabled) {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(option.label)
                                option.unavailableReason?.let { reason ->
                                    Text(
                                        text = reason,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        },
                        enabled = option.enabled,
                        onClick = {
                            onValueSelected(option.value)
                            expanded = false
                        }
                    )
                }
            }
        }

        supportingText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}
