package it.polito.ppemobile.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import it.polito.ppemobile.models.AcquisitionConfig
import it.polito.ppemobile.models.enums.AcquisitionState
import it.polito.ppemobile.models.enums.ProcessingSegment
import it.polito.ppemobile.ui.components.AcquisitionControls
import it.polito.ppemobile.ui.components.CameraPreview
import it.polito.ppemobile.ui.components.DetectionOverlay
import it.polito.ppemobile.ui.components.InfoPanel
import it.polito.ppemobile.ui.components.ProcessingStatusOverlay
import it.polito.ppemobile.ui.viewmodel.AcquisitionViewModel
import it.polito.ppemobile.ui.components.AcquisitionSummaryCard
import it.polito.ppemobile.ui.components.ShareExportButton

@Composable
fun AcquisitionScreen(
    configuration: AcquisitionConfig?,
    modifier: Modifier = Modifier,
    viewModel: AcquisitionViewModel = viewModel()
) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Acquisition",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (hasCameraPermission) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onFrameAvailable = { imageProxy ->
                            viewModel.processFrame(imageProxy)
                        }
                    )

                    DetectionOverlay(
                        detectionResult = viewModel.detectionResult,
                        modifier = Modifier.fillMaxSize()
                    )

                    ProcessingStatusOverlay(
                        processingSegment = ProcessingSegment.LOCAL,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )
                }
            } else {
                CameraPermissionRequest(
                    onGrantPermissionClick = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        InfoPanel(
            configuration = configuration,
            metrics = viewModel.metrics,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Processed frames: ${viewModel.frameCounter}",
            style = MaterialTheme.typography.labelMedium
        )

        viewModel.currentAcquisition?.let { acquisition ->
            Text(
                text = "Acquisition saved: ${acquisition.acquisitionId}",
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            AcquisitionSummaryCard(
                acquisition = acquisition,
                modifier = Modifier.fillMaxWidth()
            )


            }

        viewModel.exportedZipFile?.let { zipFile ->
            Text(
                text = "Exported ZIP: ${zipFile.name}",
                style = MaterialTheme.typography.labelMedium
            )

            ShareExportButton(
                exportedFile = zipFile
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        AcquisitionControls(
            acquisitionState = viewModel.acquisitionState,
            onStartClick = {
                viewModel.startAcquisition(configuration)
            },
            onStopClick = {
                viewModel.stopAcquisition()
            },
            modifier = Modifier.padding(bottom = 20.dp)
        )
    }
}

@Composable
private fun CameraPermissionRequest(
    onGrantPermissionClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Camera permission is required to start the acquisition.")

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onGrantPermissionClick
        ) {
            Text("Grant camera permission")
        }
    }
}