package it.polito.ppemobile.domain

import android.os.Build
import it.polito.ppemobile.models.Acquisition
import it.polito.ppemobile.models.AcquisitionConfig
import it.polito.ppemobile.models.AcquisitionSummary
import it.polito.ppemobile.models.FrameResult
import java.util.UUID

class AcquisitionManager {

    private var acquisitionId: String? = null
    private var startTime: Long? = null
    private var configuration: AcquisitionConfig? = null
    private val frames = mutableListOf<FrameResult>()

    fun start(config: AcquisitionConfig) {
        acquisitionId = "EXP_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        startTime = System.currentTimeMillis()
        configuration = config
        frames.clear()
    }

    fun addFrame(frameResult: FrameResult) {
        frames.add(frameResult)
    }

    fun stop(): Acquisition? {
        val id = acquisitionId ?: return null
        val start = startTime ?: return null
        val config = configuration ?: return null

        val finishTime = System.currentTimeMillis()

        return Acquisition(
            acquisitionId = id,
            startTime = start,
            finishTime = finishTime,
            deviceName = Build.MODEL ?: "Unknown Android device",
            configuration = config,
            summary = buildSummary(frames)
        )
    }

    fun getFrameCount(): Int {
        return frames.size
    }

    fun getFrames(): List<FrameResult> {
        return frames.toList()
    }

    private fun buildSummary(frames: List<FrameResult>): AcquisitionSummary {
        if (frames.isEmpty()) {
            return AcquisitionSummary(
                averageFps = null,
                averageCpu = null,
                averageRam = null,
                average5GSignal = null,
                averageRtt = null,
                averageBattery = null,
                averageTemperature = null,
                totalBatteryConsumption = null,
                averagePpeConfidence = null,
                averageComplexity = null,
                averageLatency = null,
                totalUplinkData = 0L,
                totalDownlinkData = 0L,
                offloadedFrames = 0,
                discardedFrames = 0
            )
        }

        return AcquisitionSummary(
            averageFps = null,
            averageCpu = frames.mapNotNull { it.metricsSnapshot.cpuUsage }.averageOrNull(),
            averageRam = frames.mapNotNull { it.metricsSnapshot.ramUsage }.averageOrNull(),
            average5GSignal = frames.mapNotNull { it.metricsSnapshot.networkStatus.signalStrength }.averageOrNull(),
            averageRtt = frames.mapNotNull { it.metricsSnapshot.networkStatus.rtt }.averageOrNull(),
            averageBattery = frames.mapNotNull { it.metricsSnapshot.batteryLevel }.averageOrNull(),
            averageTemperature = frames.mapNotNull { it.metricsSnapshot.deviceTemperature }.averageOrNull(),
            totalBatteryConsumption = computeBatteryConsumption(frames),
            averagePpeConfidence = computeAveragePpeConfidence(frames),
            averageComplexity = frames.mapNotNull { it.complexity }.averageOrNull(),
            averageLatency = frames.mapNotNull { it.inferenceTime?.toFloat() }.averageOrNull(),
            totalUplinkData = frames.sumOf { it.metricsSnapshot.networkStatus.uploadedBytes },
            totalDownlinkData = frames.sumOf { it.metricsSnapshot.networkStatus.downloadedBytes },
            offloadedFrames = frames.count { it.processingSegment.name == "REMOTE" },
            discardedFrames = frames.count { !it.inOrder }
        )
    }

    private fun computeBatteryConsumption(frames: List<FrameResult>): Float? {
        val batteryValues = frames.mapNotNull { it.metricsSnapshot.batteryLevel }
        if (batteryValues.size < 2) return null
        return batteryValues.first() - batteryValues.last()
    }

    private fun computeAveragePpeConfidence(frames: List<FrameResult>): Float? {
        val confidences = frames.flatMap { frame ->
            frame.detectionResult.persons.flatMap { person ->
                person.ppeDetections.map { ppe -> ppe.confidence }
            }
        }

        return confidences.averageOrNull()
    }

    private fun List<Float>.averageOrNull(): Float? {
        if (isEmpty()) return null
        return average().toFloat()
    }
}