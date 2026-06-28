package it.polito.ppemobile.models

import it.polito.ppemobile.models.enums.ThemeMode

data class AppSettings(
    val remoteServerIp: String,
    val remoteServerPort: Int,
    val cloudEndpoint: String?,
    val defaultExportPath: String?,
    val themeMode: ThemeMode,
    val saveFrames: Boolean,
    val saveAnnotatedVideo: Boolean,
    val appVersion: String
)