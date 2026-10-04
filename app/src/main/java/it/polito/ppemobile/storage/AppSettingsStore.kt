package it.polito.ppemobile.storage

import android.content.Context

data class RemoteServerSettings(
    val host: String,
    val port: Int
) {
    val baseUrl: String get() = "http://$host:$port"
}

class AppSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadRemoteServer(): RemoteServerSettings = RemoteServerSettings(
        host = preferences.getString(KEY_REMOTE_HOST, DEFAULT_REMOTE_HOST) ?: DEFAULT_REMOTE_HOST,
        port = preferences.getInt(KEY_REMOTE_PORT, DEFAULT_REMOTE_PORT)
    )

    fun saveRemoteServer(host: String, port: Int) {
        require(host.isNotBlank()) { "Server host cannot be empty" }
        require(port in 1..65535) { "Server port must be between 1 and 65535" }
        preferences.edit()
            .putString(KEY_REMOTE_HOST, host.trim())
            .putInt(KEY_REMOTE_PORT, port)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "app_settings"
        const val KEY_REMOTE_HOST = "remote_host"
        const val KEY_REMOTE_PORT = "remote_port"
        const val DEFAULT_REMOTE_HOST = "192.168.2.126"
        const val DEFAULT_REMOTE_PORT = 8081
    }
}
