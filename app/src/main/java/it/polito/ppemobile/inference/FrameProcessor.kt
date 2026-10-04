package it.polito.ppemobile.inference

import android.content.Context
import androidx.camera.core.ImageProxy
import it.polito.ppemobile.models.BoundingBox
import it.polito.ppemobile.models.DetectionResult
import it.polito.ppemobile.models.InferenceDiagnostics
import it.polito.ppemobile.models.PPEDetection
import it.polito.ppemobile.models.PersonDetection
import it.polito.ppemobile.models.enums.CVModel
import it.polito.ppemobile.models.enums.PPEType
import java.io.Closeable
import kotlin.math.max
import kotlin.math.min

/** Adapts flat YOLO detections to the application's person-centric PPE domain model. */
class FrameProcessor(context: Context) : Closeable {

    private val applicationContext = context.applicationContext
    private val detectorLock = Any()
    private val detectors = mutableMapOf<Pair<CVModel, it.polito.ppemobile.models.enums.LocalAcceleration>, YoloDetector>()
    @Volatile private var acceleration = it.polito.ppemobile.models.enums.LocalAcceleration.CPU

    @Volatile
    private var activeModel: CVModel = CVModel.YOLO26N_V2

    fun selectModel(model: CVModel, acceleration: it.polito.ppemobile.models.enums.LocalAcceleration = this.acceleration) = synchronized(detectorLock) {
        LocalModelCatalog.require(model)
        if (model != activeModel || acceleration != this.acceleration) {
            detectors.values.forEach { it.close() }
            detectors.clear()
        }
        this.acceleration = acceleration
        activeModel = model
    }

    fun warmUp(model: CVModel = activeModel) {
        LocalModelCatalog.require(model)
        detectorFor(model).warmUp()
    }

    fun executionInfo(model: CVModel = activeModel) = detectorFor(model).executionInfo

    fun processFrame(
        imageProxy: ImageProxy,
        selectedPPEs: Set<PPEType> = SUPPORTED_PPE_TYPES
    ): DetectionResult {
        val selectedModel = activeModel
        val modelDefinition = LocalModelCatalog.require(selectedModel)
        val detector = detectorFor(selectedModel)
        val modelResult = detector.detect(imageProxy)
        val people = modelResult.detections
            .filter { it.classId == PERSON_CLASS_ID }
            .sortedByDescending { it.confidence }
        val ppeDetections = modelResult.detections
            .filter { it.classId != PERSON_CLASS_ID }
            .mapNotNull { detection ->
                val ppeType = modelDefinition.ppeTypesByClassId[detection.classId]
                    ?: return@mapNotNull null
                if (ppeType !in selectedPPEs) return@mapNotNull null
                detection to ppeType
            }

        val assignedPpe = Array(people.size) { mutableListOf<PPEDetection>() }
        ppeDetections.forEach { (detection, ppeType) ->
            val ownerIndex = findOwner(detection.box, people)
            if (ownerIndex >= 0) {
                assignedPpe[ownerIndex] += PPEDetection(
                    ppeType = ppeType,
                    boundingBox = detection.box.toBoundingBox(),
                    confidence = detection.confidence
                )
            }
        }

        val personDetections = people.mapIndexed { index, person ->
            PersonDetection(
                personId = "person_$index",
                personBox = person.box.toBoundingBox(),
                ppeDetections = assignedPpe[index].sortedByDescending { it.confidence },
                globalConfidence = person.confidence
            )
        }

        return DetectionResult(
            localExecution = detector.executionInfo,
            persons = personDetections,
            frameWidth = modelResult.frameWidth,
            frameHeight = modelResult.frameHeight,
            inferenceDiagnostics = InferenceDiagnostics(
                maxConfidenceByClass = modelDefinition.classNames.mapIndexed { index, className ->
                    className to modelResult.maxConfidenceByClass[index]
                }.toMap(),
                candidatesAboveThreshold = modelResult.candidatesAboveThreshold,
                detectionsAfterNms = modelResult.detections.size
            )
        )
    }

    override fun close() = synchronized(detectorLock) {
        detectors.values.forEach { it.close() }
        detectors.clear()
    }

    private fun detectorFor(model: CVModel): YoloDetector = synchronized(detectorLock) {
        detectors.getOrPut(model to acceleration) {
            YoloDetector(
                context = applicationContext,
                modelDefinition = LocalModelCatalog.require(model),
                acceleration = acceleration
            )
        }
    }

    private fun findOwner(ppeBox: ModelBox, people: List<ModelDetection>): Int {
        val containingPeople = people.indices.filter { index ->
            val personBox = people[index].box
            ppeBox.centerX in personBox.x1..personBox.x2 &&
                ppeBox.centerY in personBox.y1..personBox.y2
        }
        if (containingPeople.isNotEmpty()) {
            return containingPeople.minBy { people[it].box.area }
        }

        val fallback = people.indices
            .map { index -> index to intersectionOverPpe(ppeBox, people[index].box) }
            .maxByOrNull { it.second }
        return if (fallback != null && fallback.second >= MIN_PPE_OVERLAP) fallback.first else -1
    }

    private fun intersectionOverPpe(ppe: ModelBox, person: ModelBox): Float {
        val intersectionWidth = (min(ppe.x2, person.x2) - max(ppe.x1, person.x1)).coerceAtLeast(0f)
        val intersectionHeight = (min(ppe.y2, person.y2) - max(ppe.y1, person.y1)).coerceAtLeast(0f)
        return if (ppe.area > 0f) (intersectionWidth * intersectionHeight) / ppe.area else 0f
    }

    private fun ModelBox.toBoundingBox() = BoundingBox(
        x = x1,
        y = y1,
        width = width,
        height = height
    )

    private companion object {
        const val PERSON_CLASS_ID = 0
        const val MIN_PPE_OVERLAP = 0.5f
        val SUPPORTED_PPE_TYPES = setOf(
            PPEType.HELMET,
            PPEType.SAFETY_VEST,
            PPEType.GLOVES,
            PPEType.SHOES,
            PPEType.BOOTS
        )
    }
}
