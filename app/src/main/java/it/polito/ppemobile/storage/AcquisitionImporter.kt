package it.polito.ppemobile.storage

import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

data class ExportedAcquisitionInfo(
    val file: File,
    val acquisitionId: String,
    val deviceName: String,
    val startTime: Long,
    val finishTime: Long?,
    val model: String,
    val strategy: String,
    val runtime: String,
    val averageCpu: Float?,
    val averageRam: Float?,
    val averageLatency: Float?,
    val averagePpeConfidence: Float?,
    val frameCount: Int,
    val firstFrameTimestamp: Long?,
    val lastFrameTimestamp: Long?,
    val averageInferenceTimeFromFrames: Float?
)

class AcquisitionImporter {

    fun readInfo(zipFile: File): ExportedAcquisitionInfo? {
        return try {
            ZipFile(zipFile).use { zip ->
                val metadataText = zip.getInputStream(zip.getEntry("metadata.json"))
                    .bufferedReader()
                    .readText()

                val summaryText = zip.getInputStream(zip.getEntry("summary.json"))
                    .bufferedReader()
                    .readText()

                val framesEntry = zip.getEntry("frames.jsonl")
                val frameLines = framesEntry
                    ?.let {
                        zip.getInputStream(it)
                            .bufferedReader()
                            .readLines()
                            .filter { line -> line.isNotBlank() }
                    }
                    ?: emptyList()

                val frameJsonObjects = frameLines.mapNotNull { line ->
                    try {
                        JSONObject(line)
                    } catch (e: Exception) {
                        null
                    }
                }

                val frameCount = frameJsonObjects.size

                val firstFrameTimestamp = frameJsonObjects
                    .firstOrNull()
                    ?.optLongOrNull("timestampGeneration")

                val lastFrameTimestamp = frameJsonObjects
                    .lastOrNull()
                    ?.optLongOrNull("timestampGeneration")

                val averageInferenceTimeFromFrames = frameJsonObjects
                    .mapNotNull { it.optFloatOrNull("inferenceTime") }
                    .averageOrNull()

                val metadata = JSONObject(metadataText)
                val summary = JSONObject(summaryText)
                val config = metadata.getJSONObject("configuration")

                ExportedAcquisitionInfo(
                    file = zipFile,
                    acquisitionId = metadata.getString("acquisitionId"),
                    deviceName = metadata.getString("deviceName"),
                    startTime = metadata.getLong("startTime"),
                    finishTime = if (metadata.isNull("finishTime")) null else metadata.getLong("finishTime"),
                    model = config.getString("cvModel"),
                    strategy = config.getString("offloadingStrategy"),
                    runtime = config.getString("runtime"),
                    averageCpu = summary.optFloatOrNull("averageCpu"),
                    averageRam = summary.optFloatOrNull("averageRam"),
                    averageLatency = summary.optFloatOrNull("averageLatency"),
                    averagePpeConfidence = summary.optFloatOrNull("averagePpeConfidence"),
                    frameCount = frameCount,
                    firstFrameTimestamp = firstFrameTimestamp,
                    lastFrameTimestamp = lastFrameTimestamp,
                    averageInferenceTimeFromFrames = averageInferenceTimeFromFrames
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun JSONObject.optFloatOrNull(name: String): Float? {
        return if (isNull(name)) null else optDouble(name).toFloat()
    }

    private fun JSONObject.optLongOrNull(name: String): Long? {
        return if (isNull(name)) null else optLong(name)
    }

    private fun List<Float>.averageOrNull(): Float? {
        if (isEmpty()) return null
        return average().toFloat()
    }
}