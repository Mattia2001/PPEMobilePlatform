package it.polito.ppemobile.metrics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import it.polito.ppemobile.models.MetricsSnapshot
import it.polito.ppemobile.models.NetworkStatus

class DeviceMetricsSampler(
    private val context: Context
) {
    private var lastCpuTimeMs: Long = Process.getElapsedCpuTime()
    private var lastWallTimeMs: Long = System.currentTimeMillis()

    fun sample(): MetricsSnapshot {
        val nowCpuTimeMs = Process.getElapsedCpuTime()
        val nowWallTimeMs = System.currentTimeMillis()

        val cpuDelta = nowCpuTimeMs - lastCpuTimeMs
        val wallDelta = nowWallTimeMs - lastWallTimeMs

        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        val cpuUsage = if (wallDelta > 0) {
            ((cpuDelta.toFloat() / wallDelta.toFloat()) * 100f / cores)
                .coerceIn(0f, 100f)
        } else {
            null
        }

        lastCpuTimeMs = nowCpuTimeMs
        lastWallTimeMs = nowWallTimeMs

        val runtime = Runtime.getRuntime()
        val usedRamMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)

        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val batteryLevel = batteryIntent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) level * 100f / scale else null
        }

        val batteryTemperature = batteryIntent?.let {
            val tempTenths = it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            if (tempTenths >= 0) tempTenths / 10f else null
        }

        return MetricsSnapshot(
            cpuUsage = cpuUsage,
            ramUsage = usedRamMb,
            batteryLevel = batteryLevel,
            batteryTemperature = batteryTemperature,
            deviceTemperature = batteryTemperature,
            networkStatus = NetworkStatus(
                networkType = "Unknown",
                signalStrength = null,
                rtt = null,
                uplinkThroughput = null,
                downlinkThroughput = null,
                uploadedBytes = 0L,
                downloadedBytes = 0L
            )
        )
    }
}