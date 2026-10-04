package it.polito.ppemobile.inference

import it.polito.ppemobile.models.enums.CVModel
import it.polito.ppemobile.models.enums.PPEType

data class LocalModelDefinition(
    val model: CVModel,
    val assetPath: String,
    val metadataAssetPath: String,
    val datasetLabel: String,
    val inputSize: Int,
    val outputCandidates: Int,
    val classNames: List<String>,
    val ppeTypesByClassId: Map<Int, PPEType>,
    val evaluation: ModelEvaluationSummary
)

data class PerClassMetric(
    val className: String,
    val map50To95: Double
)

data class ModelEvaluationSummary(
    val testSetLabel: String,
    val images: Int,
    val annotations: Int,
    val precision: Double,
    val recall: Double,
    val map50: Double,
    val map50To95: Double,
    val perClass: List<PerClassMetric>
)

/** Single source of truth for models that are bundled and executable on the device. */
object LocalModelCatalog {
    private val v2ClassNames = listOf(
        "person", "helmet", "safety_vest", "gloves", "safety_boots"
    )
    private val v2PpeTypes = mapOf(
        1 to PPEType.HELMET,
        2 to PPEType.SAFETY_VEST,
        3 to PPEType.GLOVES,
        4 to PPEType.BOOTS
    )

    private val definitions = linkedMapOf(
        CVModel.YOLO26N_V2 to LocalModelDefinition(
            model = CVModel.YOLO26N_V2,
            assetPath = "models/yolo26n_sh17_pictor_construction_v2_best_fp32.tflite",
            metadataAssetPath = "models/yolo26n_sh17_pictor_construction_v2_best_fp32.json",
            datasetLabel = "SH17 + Pictor-PPE + Construction-PPE (v2)",
            inputSize = 640,
            outputCandidates = 8400,
            classNames = v2ClassNames,
            ppeTypesByClassId = v2PpeTypes,
            evaluation = ModelEvaluationSummary(
                testSetLabel = "Unified PPE v2 test",
                images = 1107,
                annotations = 3446,
                precision = 0.850233,
                recall = 0.697558,
                map50 = 0.761983,
                map50To95 = 0.465851,
                perClass = listOf(
                    PerClassMetric("person", 0.654613),
                    PerClassMetric("helmet", 0.430808),
                    PerClassMetric("safety vest", 0.525664),
                    PerClassMetric("gloves", 0.331322),
                    PerClassMetric("safety boots", 0.386846)
                )
            )
        ),
        CVModel.YOLO11N_V2 to LocalModelDefinition(
            model = CVModel.YOLO11N_V2,
            assetPath = "models/yolo11n_sh17_pictor_construction_v2_640_best_fp32.tflite",
            metadataAssetPath = "models/yolo11n_sh17_pictor_construction_v2_640_best_fp32.json",
            datasetLabel = "SH17 + Pictor-PPE + Construction-PPE (v2)",
            inputSize = 640,
            outputCandidates = 8400,
            classNames = v2ClassNames,
            ppeTypesByClassId = v2PpeTypes,
            evaluation = ModelEvaluationSummary(
                testSetLabel = "Frozen unified PPE v2 test",
                images = 1107,
                annotations = 3446,
                precision = 0.844785,
                recall = 0.668930,
                map50 = 0.752135,
                map50To95 = 0.457847,
                perClass = listOf(
                    PerClassMetric("person", 0.642734),
                    PerClassMetric("helmet", 0.419878),
                    PerClassMetric("safety vest", 0.533917),
                    PerClassMetric("gloves", 0.306665),
                    PerClassMetric("safety boots", 0.386039)
                )
            )
        ),
        CVModel.YOLO26N_V2_960 to LocalModelDefinition(
            model = CVModel.YOLO26N_V2_960,
            assetPath = "models/yolo26n_sh17_pictor_construction_v2_960_best_fp32.tflite",
            metadataAssetPath = "models/yolo26n_sh17_pictor_construction_v2_960_best_fp32.json",
            datasetLabel = "SH17 + Pictor-PPE + Construction-PPE (v2)",
            inputSize = 960,
            outputCandidates = 18900,
            classNames = v2ClassNames,
            ppeTypesByClassId = v2PpeTypes,
            evaluation = ModelEvaluationSummary(
                testSetLabel = "Frozen unified PPE v2 test",
                images = 1107,
                annotations = 3446,
                precision = 0.862672,
                recall = 0.724169,
                map50 = 0.798944,
                map50To95 = 0.494422,
                perClass = listOf(
                    PerClassMetric("person", 0.671464),
                    PerClassMetric("helmet", 0.490715),
                    PerClassMetric("safety vest", 0.541713),
                    PerClassMetric("gloves", 0.376033),
                    PerClassMetric("safety boots", 0.392187)
                )
            )
        )
    )

    val availableModels: List<CVModel> = definitions.keys.toList()

    fun isAvailable(model: CVModel): Boolean = model in definitions

    fun require(model: CVModel): LocalModelDefinition = definitions[model]
        ?: throw IllegalArgumentException("Model ${model.name} is not bundled in this build.")
}
