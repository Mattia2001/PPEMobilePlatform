package it.polito.ppemobile.remote

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import it.polito.ppemobile.models.BoundingBox
import it.polito.ppemobile.models.DetectionResult
import it.polito.ppemobile.models.InferenceDiagnostics
import it.polito.ppemobile.models.PPEDetection
import it.polito.ppemobile.models.PersonDetection
import it.polito.ppemobile.models.RemoteInferenceMetrics
import it.polito.ppemobile.models.enums.PPEType
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class EncodedRemoteFrame(
    val bytes: ByteArray,
    val encodeMs: Float
)

data class RemoteServerStatus(
    val ready: Boolean,
    val apiVersion: String,
    val modelId: String,
    val modelVersion: String,
    val runtime: String,
    val device: String,
    val inputSize: Int,
    val gpuName: String?,
    val gpuUtilizationPercent: Int?,
    val gpuMemoryUsedMiB: Float?,
    val gpuMemoryTotalMiB: Float?,
    val roundTripMs: Float
)

data class RemoteInferenceResult(
    val detectionResult: DetectionResult,
    val metrics: RemoteInferenceMetrics
)

class RemoteInferenceClient(
    private val baseUrl: String,
    private val connectTimeoutMs: Int = 2_500,
    private val readTimeoutMs: Int = 5_000
) {
    fun encodeFrame(imageProxy: ImageProxy, jpegQuality: Int): EncodedRemoteFrame {
        val started = SystemClock.elapsedRealtimeNanos()
        val rawBitmap = imageProxy.toBitmap()
        val orientedBitmap = rotateBitmap(rawBitmap, imageProxy.imageInfo.rotationDegrees)
        if (orientedBitmap !== rawBitmap) rawBitmap.recycle()
        val output = ByteArrayOutputStream()
        try {
            check(orientedBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(1, 100), output)) {
                "Unable to encode camera frame as JPEG"
            }
            return EncodedRemoteFrame(
                bytes = output.toByteArray(),
                encodeMs = elapsedMs(started)
            )
        } finally {
            orientedBitmap.recycle()
            output.close()
        }
    }

    fun status(): RemoteServerStatus {
        val started = SystemClock.elapsedRealtimeNanos()
        val connection = openConnection("/v1/status").apply {
            requestMethod = "GET"
        }
        val (statusCode, responseBytes) = execute(connection)
        check(statusCode in 200..299) { apiError(statusCode, responseBytes) }
        val body = JSONObject(responseBytes.toString(Charsets.UTF_8))
        val model = body.getJSONObject("model")
        val gpu = body.optJSONObject("gpu")
        return RemoteServerStatus(
            ready = body.optString("status") == "ready",
            apiVersion = body.getString("apiVersion"),
            modelId = model.getString("modelId"),
            modelVersion = model.getString("modelVersion"),
            runtime = model.getString("runtime"),
            device = model.getString("device"),
            inputSize = model.getInt("inputSize"),
            gpuName = gpu?.optString("name")?.takeIf { it.isNotBlank() },
            gpuUtilizationPercent = gpu?.optInt("utilizationPercent"),
            gpuMemoryUsedMiB = gpu?.optDouble("memoryUsedMiB")?.toFloat(),
            gpuMemoryTotalMiB = gpu?.optDouble("memoryTotalMiB")?.toFloat(),
            roundTripMs = elapsedMs(started)
        )
    }

    fun infer(
        encodedFrame: EncodedRemoteFrame,
        frameId: String,
        sequenceNumber: Int,
        captureTimestampMs: Long,
        modelId: String,
        selectedClasses: List<String>
    ): RemoteInferenceResult {
        val boundary = "PPE-${UUID.randomUUID()}"
        val metadata = JSONObject().apply {
            put("apiVersion", API_VERSION)
            put("frameId", frameId)
            put("sequenceNumber", sequenceNumber)
            put("captureTimestampMs", captureTimestampMs)
            put("modelId", modelId)
            put("selectedClasses", JSONArray(selectedClasses))
        }.toString()

        val requestBody = ByteArrayOutputStream().use { output ->
            fun text(value: String) = output.write(value.toByteArray(Charsets.UTF_8))
            text("--$boundary\r\n")
            text("Content-Disposition: form-data; name=\"metadata\"\r\n")
            text("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            text(metadata)
            text("\r\n--$boundary\r\n")
            text("Content-Disposition: form-data; name=\"frame\"; filename=\"$frameId.jpg\"\r\n")
            text("Content-Type: image/jpeg\r\n\r\n")
            output.write(encodedFrame.bytes)
            text("\r\n--$boundary--\r\n")
            output.toByteArray()
        }

        val started = SystemClock.elapsedRealtimeNanos()
        val connection = openConnection("/v1/inference").apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setFixedLengthStreamingMode(requestBody.size)
        }
        connection.outputStream.use { it.write(requestBody) }
        val (statusCode, responseBytes) = execute(connection)
        val roundTripMs = elapsedMs(started)
        check(statusCode in 200..299) { apiError(statusCode, responseBytes) }
        return parseInference(
            body = JSONObject(responseBytes.toString(Charsets.UTF_8)),
            encodedFrame = encodedFrame,
            encodedBytes = encodedFrame.bytes.size.toLong(),
            responseBytes = responseBytes.size.toLong(),
            roundTripMs = roundTripMs
        )
    }

    private fun parseInference(
        body: JSONObject,
        encodedFrame: EncodedRemoteFrame,
        encodedBytes: Long,
        responseBytes: Long,
        roundTripMs: Float
    ): RemoteInferenceResult {
        val detections = body.getJSONArray("detections")
        val personById = linkedMapOf<String, MutablePerson>()
        val maxConfidence = linkedMapOf<String, Float>()
        for (index in 0 until detections.length()) {
            val detection = detections.getJSONObject(index)
            val className = detection.getString("className")
            val confidence = detection.getDouble("confidence").toFloat()
            maxConfidence[className] = maxOf(maxConfidence[className] ?: 0f, confidence)
            if (className == "person") {
                val id = detection.getString("detectionId")
                personById[id] = MutablePerson(
                    id = id,
                    box = detection.getJSONObject("boxNormalized").toBoundingBox(),
                    confidence = confidence
                )
            }
        }
        for (index in 0 until detections.length()) {
            val detection = detections.getJSONObject(index)
            val ppeType = detection.getString("className").toPpeType() ?: continue
            val personId = detection.optString("associatedPersonId").takeIf { it.isNotBlank() } ?: continue
            personById[personId]?.ppe?.add(
                PPEDetection(
                    ppeType = ppeType,
                    boundingBox = detection.getJSONObject("boxNormalized").toBoundingBox(),
                    confidence = detection.getDouble("confidence").toFloat()
                )
            )
        }
        val image = body.getJSONObject("image")
        val timing = body.getJSONObject("timing")
        val model = body.getJSONObject("model")
        val totalServerMs = timing.getDouble("totalServerMs").toFloat()
        return RemoteInferenceResult(
            detectionResult = DetectionResult(
                persons = personById.values.map { person ->
                    PersonDetection(
                        personId = person.id,
                        personBox = person.box,
                        ppeDetections = person.ppe.sortedByDescending { it.confidence },
                        globalConfidence = person.confidence
                    )
                },
                frameWidth = image.getInt("width"),
                frameHeight = image.getInt("height"),
                inferenceDiagnostics = InferenceDiagnostics(
                    maxConfidenceByClass = maxConfidence,
                    candidatesAboveThreshold = detections.length(),
                    detectionsAfterNms = detections.length()
                )
            ),
            metrics = RemoteInferenceMetrics(
                apiVersion = body.getString("apiVersion"),
                modelId = model.getString("modelId"),
                modelVersion = model.getString("modelVersion"),
                device = model.getString("device"),
                inputSize = model.getInt("inputSize"),
                encodedBytes = encodedBytes,
                responseBytes = responseBytes,
                encodeMs = encodedFrame.encodeMs,
                roundTripMs = roundTripMs,
                transportAndApiOverheadMs = (roundTripMs - totalServerMs).coerceAtLeast(0f),
                queueMs = timing.getDouble("queueMs").toFloat(),
                decodeMs = timing.getDouble("decodeMs").toFloat(),
                preprocessMs = timing.getDouble("preprocessMs").toFloat(),
                inferenceMs = timing.getDouble("inferenceMs").toFloat(),
                postprocessMs = timing.getDouble("postprocessMs").toFloat(),
                totalServerMs = totalServerMs,
                serverReceiveTimestampMs = body.getLong("serverReceiveTimestampMs"),
                serverCompleteTimestampMs = body.getLong("serverCompleteTimestampMs")
            )
        )
    }

    private fun openConnection(path: String): HttpURLConnection =
        (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            useCaches = false
            setRequestProperty("Accept", "application/json")
        }

    private fun execute(connection: HttpURLConnection): Pair<Int, ByteArray> = try {
        val statusCode = connection.responseCode
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        statusCode to (stream?.use { it.readBytes() } ?: ByteArray(0))
    } finally {
        connection.disconnect()
    }

    private fun apiError(statusCode: Int, responseBytes: ByteArray): String {
        val raw = responseBytes.toString(Charsets.UTF_8)
        val message = runCatching {
            JSONObject(raw).getJSONObject("error").getString("message")
        }.getOrNull()
        return "Remote server HTTP $statusCode${message?.let { ": $it" } ?: ""}"
    }

    private fun JSONObject.toBoundingBox() = BoundingBox(
        x = getDouble("x").toFloat(),
        y = getDouble("y").toFloat(),
        width = getDouble("width").toFloat(),
        height = getDouble("height").toFloat()
    )

    private fun rotateBitmap(source: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees % 360 == 0) return source
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun elapsedMs(startedNanos: Long): Float =
        (SystemClock.elapsedRealtimeNanos() - startedNanos) / 1_000_000f

    private data class MutablePerson(
        val id: String,
        val box: BoundingBox,
        val confidence: Float,
        val ppe: MutableList<PPEDetection> = mutableListOf()
    )

    private fun String.toPpeType(): PPEType? = when (this) {
        "helmet" -> PPEType.HELMET
        "safety_vest" -> PPEType.SAFETY_VEST
        "gloves" -> PPEType.GLOVES
        "safety_boots" -> PPEType.BOOTS
        else -> null
    }

    companion object {
        const val API_VERSION = "1.0"
        const val YOLO26N_640_MODEL_ID = "yolo26n-ppe-v2-640"
    }
}
