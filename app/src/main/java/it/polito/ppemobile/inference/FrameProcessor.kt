package it.polito.ppemobile.inference

import it.polito.ppemobile.models.BoundingBox
import it.polito.ppemobile.models.DetectionResult
import it.polito.ppemobile.models.PPEDetection
import it.polito.ppemobile.models.PersonDetection
import it.polito.ppemobile.models.enums.PPEType
import androidx.camera.core.ImageProxy

class FrameProcessor {

    fun processFrame(imageProxy: ImageProxy): DetectionResult {
        val personDetection = PersonDetection(
            personId = "person_0",
            personBox = BoundingBox(
                x = 0.20f,
                y = 0.18f,
                width = 0.55f,
                height = 0.70f
            ),
            ppeDetections = listOf(
                PPEDetection(
                    ppeType = PPEType.HELMET,
                    boundingBox = BoundingBox(
                        x = 0.35f,
                        y = 0.20f,
                        width = 0.22f,
                        height = 0.14f
                    ),
                    confidence = 0.91f
                ),
                PPEDetection(
                    ppeType = PPEType.SAFETY_VEST,
                    boundingBox = BoundingBox(
                        x = 0.32f,
                        y = 0.42f,
                        width = 0.28f,
                        height = 0.30f
                    ),
                    confidence = 0.87f
                )
            ),
            globalConfidence = 0.94f
        )

        return DetectionResult(
            persons = listOf(personDetection)
        )
    }
}