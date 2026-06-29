package it.polito.ppemobile.storage

import android.content.Context
import it.polito.ppemobile.models.Acquisition
import it.polito.ppemobile.models.FrameResult
import java.io.File

class StorageManager(
    private val context: Context
) {
    private val exporter = AcquisitionExporter(context)
    private val importer = AcquisitionImporter()

    fun exportAcquisition(
        acquisition: Acquisition,
        frames: List<FrameResult>
    ): File {
        return exporter.exportAcquisition(
            acquisition = acquisition,
            frames = frames
        )
    }

    fun listExportedAcquisitions(): List<File> {
        val exportsDir = File(context.filesDir, "exports")

        if (!exportsDir.exists()) {
            return emptyList()
        }

        return exportsDir
            .listFiles()
            ?.filter { it.extension == "zip" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun listExportedAcquisitionInfo(): List<ExportedAcquisitionInfo> {
        return listExportedAcquisitions()
            .mapNotNull { file ->
                importer.readInfo(file)
            }
    }

    fun getExportsDirectory(): File {
        return File(context.filesDir, "exports")
    }

    fun deleteExportedAcquisition(file: File): Boolean {
        return file.exists() && file.delete()
    }
}