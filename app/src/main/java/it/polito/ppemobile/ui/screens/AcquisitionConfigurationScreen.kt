package it.polito.ppemobile.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.polito.ppemobile.models.AcquisitionConfig
import it.polito.ppemobile.models.enums.CVModel
import it.polito.ppemobile.models.enums.ExportFormat
import it.polito.ppemobile.models.enums.OffloadingStrategy
import it.polito.ppemobile.models.enums.PPEType
import it.polito.ppemobile.models.enums.Runtime

@Composable
fun AcquisitionConfigurationScreen(
    onStartAcquisitionClick: (AcquisitionConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedModel by remember { mutableStateOf("YOLO11n") }
    var selectedRuntime by remember { mutableStateOf("ONNX Runtime") }
    var selectedStrategy by remember { mutableStateOf("Always Local") }
    var selectedQuality by remember { mutableStateOf("1080p") }
    var selectedFps by remember { mutableStateOf("30 FPS") }
    var selectedCompression by remember { mutableStateOf("JPEG 80") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Acquisition Configuration",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                ConfigDropdown(
                    label = "Model",
                    selectedValue = selectedModel,
                    options = listOf("YOLO11n", "YOLO11s", "YOLO26n"),
                    onValueSelected = { selectedModel = it }
                )

                ConfigDropdown(
                    label = "Runtime",
                    selectedValue = selectedRuntime,
                    options = listOf("ONNX Runtime", "TensorFlow Lite"),
                    onValueSelected = { selectedRuntime = it }
                )

                ConfigDropdown(
                    label = "Offloading Strategy",
                    selectedValue = selectedStrategy,
                    options = listOf("Always Local", "Always Remote", "Adaptive"),
                    onValueSelected = { selectedStrategy = it }
                )

                ConfigDropdown(
                    label = "Video Quality",
                    selectedValue = selectedQuality,
                    options = listOf("720p", "1080p", "4K"),
                    onValueSelected = { selectedQuality = it }
                )

                ConfigDropdown(
                    label = "FPS",
                    selectedValue = selectedFps,
                    options = listOf("15 FPS", "30 FPS", "60 FPS"),
                    onValueSelected = { selectedFps = it }
                )

                ConfigDropdown(
                    label = "Compression",
                    selectedValue = selectedCompression,
                    options = listOf("JPEG 60", "JPEG 80", "JPEG 95"),
                    onValueSelected = { selectedCompression = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val config = AcquisitionConfig(
                    offloadingStrategy = when (selectedStrategy) {
                        "Always Remote" -> OffloadingStrategy.ALWAYS_REMOTE
                        "Adaptive" -> OffloadingStrategy.ADAPTIVE
                        else -> OffloadingStrategy.ALWAYS_LOCAL
                    },
                    cvModel = when (selectedModel) {
                        "YOLO11s" -> CVModel.YOLO11S
                        "YOLO26n" -> CVModel.YOLO26N
                        else -> CVModel.YOLO11N
                    },
                    runtime = when (selectedRuntime) {
                        "TensorFlow Lite" -> Runtime.TFLITE
                        else -> Runtime.ONNX_RUNTIME
                    },
                    fps = selectedFps.substringBefore(" ").toInt(),
                    videoQuality = selectedQuality,
                    compressionLevel = selectedCompression.substringAfter("JPEG ").toInt(),
                    selectedPPEs = listOf(
                        PPEType.HELMET,
                        PPEType.SAFETY_VEST
                    ),
                    slidingWindowEnabled = false,
                    majorityVotingEnabled = false,
                    exportFormat = ExportFormat.JSONL
                )

                onStartAcquisitionClick(config)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Acquisition")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onValueSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedValue,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onValueSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}