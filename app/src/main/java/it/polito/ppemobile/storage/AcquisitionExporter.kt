package it.polito.ppemobile.storage

import android.content.Context
import it.polito.ppemobile.models.Acquisition
import it.polito.ppemobile.models.FrameResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AcquisitionExporter(
    private val context: Context
) {
    fun exportAcquisition(
        acquisition: Acquisition,
        frames: List<FrameResult>
    ): File {
        val exportDir = File(
            context.filesDir,
            "exports/${acquisition.acquisitionId}"
        )

        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }

        File(exportDir, "metadata.json").writeText(
            buildMetadataJson(acquisition).toString(4)
        )

        File(exportDir, "summary.json").writeText(
            buildSummaryJson(acquisition).toString(4)
        )

        File(exportDir, "frames.jsonl").writeText(
            buildFramesJsonl(frames)
        )

        return zipDirectory(exportDir)
    }

    private fun buildMetadataJson(acquisition: Acquisition): JSONObject {
        return JSONObject().apply {
            put("acquisitionId", acquisition.acquisitionId)
            put("startTime", acquisition.startTime)
            put("finishTime", acquisition.finishTime)
            put("deviceName", acquisition.deviceName)

            put("configuration", JSONObject().apply {
                put("offloadingStrategy", acquisition.configuration.offloadingStrategy.name)
                put("cvModel", acquisition.configuration.cvModel.name)
                put("runtime", acquisition.configuration.runtime.name)
                put("fps", acquisition.configuration.fps)
                put("videoQuality", acquisition.configuration.videoQuality)
                put("compressionLevel", acquisition.configuration.compressionLevel)
                put("selectedPPEs", JSONArray(acquisition.configuration.selectedPPEs.map { it.name }))
                put("slidingWindowEnabled", acquisition.configuration.slidingWindowEnabled)
                put("majorityVotingEnabled", acquisition.configuration.majorityVotingEnabled)
                put("exportFormat", acquisition.configuration.exportFormat.name)
            })
        }
    }

    private fun buildSummaryJson(acquisition: Acquisition): JSONObject {
        val summary = acquisition.summary

        return JSONObject().apply {
            put("averageFps", summary?.averageFps)
            put("averageCpu", summary?.averageCpu)
            put("averageRam", summary?.averageRam)
            put("average5GSignal", summary?.average5GSignal)
            put("averageRtt", summary?.averageRtt)
            put("averageBattery", summary?.averageBattery)
            put("averageTemperature", summary?.averageTemperature)
            put("totalBatteryConsumption", summary?.totalBatteryConsumption)
            put("averagePpeConfidence", summary?.averagePpeConfidence)
            put("averageComplexity", summary?.averageComplexity)
            put("averageLatency", summary?.averageLatency)
            put("totalUplinkData", summary?.totalUplinkData)
            put("totalDownlinkData", summary?.totalDownlinkData)
            put("offloadedFrames", summary?.offloadedFrames)
            put("discardedFrames", summary?.discardedFrames)
        }
    }

    private fun buildFramesJsonl(frames: List<FrameResult>): String {
        return frames.joinToString(separator = "\n") { frame ->
            buildFrameJson(frame).toString()
        }
    }

    private fun buildFrameJson(frame: FrameResult): JSONObject {
        return JSONObject().apply {
            put("frameId", frame.frameId)
            put("timestampGeneration", frame.timestampGeneration)
            put("inferenceTime", frame.inferenceTime)
            put("displayTime", frame.displayTime)
            put("imageReference", frame.imageReference)
            put("processingSegment", frame.processingSegment.name)
            put("complexity", frame.complexity)
            put("inOrder", frame.inOrder)

            put("metricsSnapshot", JSONObject().apply {
                put("cpuUsage", frame.metricsSnapshot.cpuUsage)
                put("ramUsage", frame.metricsSnapshot.ramUsage)
                put("batteryLevel", frame.metricsSnapshot.batteryLevel)
                put("batteryTemperature", frame.metricsSnapshot.batteryTemperature)
                put("deviceTemperature", frame.metricsSnapshot.deviceTemperature)

                put("networkStatus", JSONObject().apply {
                    put("networkType", frame.metricsSnapshot.networkStatus.networkType)
                    put("signalStrength", frame.metricsSnapshot.networkStatus.signalStrength)
                    put("rtt", frame.metricsSnapshot.networkStatus.rtt)
                    put("uplinkThroughput", frame.metricsSnapshot.networkStatus.uplinkThroughput)
                    put("downlinkThroughput", frame.metricsSnapshot.networkStatus.downlinkThroughput)
                    put("uploadedBytes", frame.metricsSnapshot.networkStatus.uploadedBytes)
                    put("downloadedBytes", frame.metricsSnapshot.networkStatus.downloadedBytes)
                })
            })

            put("detectionResult", JSONObject().apply {
                put("persons", JSONArray(frame.detectionResult.persons.map { person ->
                    JSONObject().apply {
                        put("personId", person.personId)
                        put("globalConfidence", person.globalConfidence)
                        put("personBox", boxToJson(person.personBox))

                        put("ppeDetections", JSONArray(person.ppeDetections.map { ppe ->
                            JSONObject().apply {
                                put("ppeType", ppe.ppeType.name)
                                put("confidence", ppe.confidence)
                                put("boundingBox", boxToJson(ppe.boundingBox))
                            }
                        }))
                    }
                }))
            })
        }
    }

    private fun boxToJson(box: it.polito.ppemobile.models.BoundingBox): JSONObject {
        return JSONObject().apply {
            put("x", box.x)
            put("y", box.y)
            put("width", box.width)
            put("height", box.height)
        }
    }

    private fun zipDirectory(directory: File): File {
        val zipFile = File(
            directory.parentFile,
            "${directory.name}.zip"
        )

        if (zipFile.exists()) {
            zipFile.delete()
        }

        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            directory
                .walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val relativePath = file.relativeTo(directory).path
                    val zipEntry = ZipEntry(relativePath)

                    zipOut.putNextEntry(zipEntry)

                    FileInputStream(file).use { input ->
                        input.copyTo(zipOut)
                    }

                    zipOut.closeEntry()
                }
        }

        return zipFile
    }
}