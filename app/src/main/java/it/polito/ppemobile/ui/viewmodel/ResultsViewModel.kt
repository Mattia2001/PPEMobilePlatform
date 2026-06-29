package it.polito.ppemobile.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import it.polito.ppemobile.storage.ExportedAcquisitionInfo
import it.polito.ppemobile.storage.StorageManager

class ResultsViewModel(application: Application) : AndroidViewModel(application) {

    private val storageManager = StorageManager(application.applicationContext)

    val exportedAcquisitions = mutableStateListOf<ExportedAcquisitionInfo>()

    fun loadExports() {
        exportedAcquisitions.clear()
        exportedAcquisitions.addAll(storageManager.listExportedAcquisitionInfo())
    }

    fun deleteExport(acquisitionInfo: ExportedAcquisitionInfo) {
        storageManager.deleteExportedAcquisition(acquisitionInfo.file)
        loadExports()
    }
}