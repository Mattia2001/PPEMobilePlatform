package it.polito.ppemobile.inference

import it.polito.ppemobile.models.enums.CVModel
import it.polito.ppemobile.models.enums.PPEType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelCatalogTest {

    @Test
    fun availableModelsContainEveryBundledArtifact() {
        assertEquals(
            listOf(
                CVModel.YOLO26N_V2,
                CVModel.YOLO11N_V2,
                CVModel.YOLO26N_V2_960
            ),
            LocalModelCatalog.availableModels
        )
        assertFalse(LocalModelCatalog.isAvailable(CVModel.YOLO11N))
        assertFalse(LocalModelCatalog.isAvailable(CVModel.YOLO11N_SH17_PICTOR))
        assertFalse(LocalModelCatalog.isAvailable(CVModel.YOLO26N))
        assertFalse(LocalModelCatalog.isAvailable(CVModel.YOLO11S))
    }

    @Test
    fun v2ModelsPublishTheirTensorContracts() {
        val model640 = LocalModelCatalog.require(CVModel.YOLO26N_V2)
        val yolo11n640 = LocalModelCatalog.require(CVModel.YOLO11N_V2)
        val model960 = LocalModelCatalog.require(CVModel.YOLO26N_V2_960)

        assertEquals(640, model640.inputSize)
        assertEquals(8400, model640.outputCandidates)
        assertEquals(640, yolo11n640.inputSize)
        assertEquals(8400, yolo11n640.outputCandidates)
        assertEquals(960, model960.inputSize)
        assertEquals(18900, model960.outputCandidates)
    }

    @Test
    fun v2ModelPublishesMetricsAndMapsClassFourToSafetyBoots() {
        val definition = LocalModelCatalog.require(CVModel.YOLO26N_V2)

        assertEquals("SH17 + Pictor-PPE + Construction-PPE (v2)", definition.datasetLabel)
        assertEquals(1107, definition.evaluation.images)
        assertEquals(3446, definition.evaluation.annotations)
        assertEquals(0.761983, definition.evaluation.map50, 0.000001)
        assertEquals(0.465851, definition.evaluation.map50To95, 0.000001)
        assertEquals("safety_boots", definition.classNames[4])
        assertEquals(PPEType.BOOTS, definition.ppeTypesByClassId[4])
    }

    @Test
    fun v2Model960PublishesFrozenAggregateAndPerClassMetrics() {
        val definition = LocalModelCatalog.require(CVModel.YOLO26N_V2_960)

        assertEquals(1107, definition.evaluation.images)
        assertEquals(3446, definition.evaluation.annotations)
        assertEquals(0.862672, definition.evaluation.precision, 0.000001)
        assertEquals(0.724169, definition.evaluation.recall, 0.000001)
        assertEquals(0.798944, definition.evaluation.map50, 0.000001)
        assertEquals(0.494422, definition.evaluation.map50To95, 0.000001)
        assertEquals(0.376033, definition.evaluation.perClass.single {
            it.className == "gloves"
        }.map50To95, 0.000001)
        assertTrue(definition.evaluation.perClass.any { it.className == "safety boots" })
    }

    @Test
    fun yolo11nV2PublishesFrozenAggregateAndPerClassMetrics() {
        val definition = LocalModelCatalog.require(CVModel.YOLO11N_V2)

        assertEquals(1107, definition.evaluation.images)
        assertEquals(3446, definition.evaluation.annotations)
        assertEquals(0.844785, definition.evaluation.precision, 0.000001)
        assertEquals(0.668930, definition.evaluation.recall, 0.000001)
        assertEquals(0.752135, definition.evaluation.map50, 0.000001)
        assertEquals(0.457847, definition.evaluation.map50To95, 0.000001)
        assertEquals(0.306665, definition.evaluation.perClass.single {
            it.className == "gloves"
        }.map50To95, 0.000001)
        assertEquals(PPEType.BOOTS, definition.ppeTypesByClassId[4])
    }
}
