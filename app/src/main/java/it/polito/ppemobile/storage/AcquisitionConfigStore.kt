package it.polito.ppemobile.storage

import android.content.Context
import it.polito.ppemobile.models.AcquisitionConfig
import it.polito.ppemobile.models.enums.CVModel
import it.polito.ppemobile.models.enums.ExportFormat
import it.polito.ppemobile.models.enums.OffloadingStrategy
import it.polito.ppemobile.models.enums.PPEType
import it.polito.ppemobile.models.enums.Runtime

/** Persists the most recently selected acquisition configuration. */
class AcquisitionConfigStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AcquisitionConfig {
        val defaults = AcquisitionConfig.default()
        val storedPpe = preferences.getStringSet(KEY_SELECTED_PPE, null)
            ?.mapNotNull { value -> enumValueOrNull<PPEType>(value) }
            ?.takeIf { it.isNotEmpty() }
            ?: defaults.selectedPPEs
        val migratedPpe = if (PPEType.SHOES in storedPpe && PPEType.BOOTS !in storedPpe) {
            storedPpe + PPEType.BOOTS
        } else {
            storedPpe
        }

        val stored = AcquisitionConfig(
            offloadingStrategy = enumValueOrNull<OffloadingStrategy>(
                preferences.getString(KEY_STRATEGY, null)
            ) ?: defaults.offloadingStrategy,
            cvModel = enumValueOrNull<CVModel>(preferences.getString(KEY_MODEL, null))
                ?: defaults.cvModel,
            runtime = enumValueOrNull<Runtime>(preferences.getString(KEY_RUNTIME, null))
                ?: defaults.runtime,
            fps = preferences.getInt(KEY_FPS, defaults.fps),
            videoQuality = preferences.getString(KEY_VIDEO_QUALITY, defaults.videoQuality)
                ?: defaults.videoQuality,
            compressionLevel = preferences.getInt(KEY_COMPRESSION, defaults.compressionLevel),
            selectedPPEs = migratedPpe,
            slidingWindowEnabled = preferences.getBoolean(
                KEY_SLIDING_WINDOW,
                defaults.slidingWindowEnabled
            ),
            majorityVotingEnabled = preferences.getBoolean(
                KEY_MAJORITY_VOTING,
                defaults.majorityVotingEnabled
            ),
            localAcceleration = enumValueOrNull<it.polito.ppemobile.models.enums.LocalAcceleration>(
                preferences.getString("local_acceleration", null)
            ) ?: defaults.localAcceleration,
            exportFormat = enumValueOrNull<ExportFormat>(
                preferences.getString(KEY_EXPORT_FORMAT, null)
            ) ?: defaults.exportFormat
        )

        val supportedLocalModel = stored.cvModel.takeIf {
            it == CVModel.YOLO26N_V2 ||
                it == CVModel.YOLO11N_V2 ||
                it == CVModel.YOLO26N_V2_960
        } ?: CVModel.YOLO26N_V2

        val supportedStrategy = stored.offloadingStrategy.takeIf {
            it == OffloadingStrategy.ALWAYS_LOCAL || it == OffloadingStrategy.ALWAYS_OFFLOAD
        } ?: OffloadingStrategy.ALWAYS_LOCAL
        val effectiveModel = if (supportedStrategy == OffloadingStrategy.ALWAYS_OFFLOAD) {
            CVModel.YOLO26N_V2
        } else {
            supportedLocalModel
        }
        return stored.copy(
            offloadingStrategy = supportedStrategy,
            cvModel = effectiveModel,
            runtime = if (supportedStrategy == OffloadingStrategy.ALWAYS_OFFLOAD) {
                Runtime.REMOTE_SERVER
            } else {
                Runtime.TFLITE
            }
        )
    }

    fun save(configuration: AcquisitionConfig) {
        preferences.edit()
            .putString("local_acceleration", configuration.localAcceleration.name)
            .putString(KEY_STRATEGY, configuration.offloadingStrategy.name)
            .putString(KEY_MODEL, configuration.cvModel.name)
            .putString(KEY_RUNTIME, configuration.runtime.name)
            .putInt(KEY_FPS, configuration.fps)
            .putString(KEY_VIDEO_QUALITY, configuration.videoQuality)
            .putInt(KEY_COMPRESSION, configuration.compressionLevel)
            .putStringSet(KEY_SELECTED_PPE, configuration.selectedPPEs.map { it.name }.toSet())
            .putBoolean(KEY_SLIDING_WINDOW, configuration.slidingWindowEnabled)
            .putBoolean(KEY_MAJORITY_VOTING, configuration.majorityVotingEnabled)
            .putString(KEY_EXPORT_FORMAT, configuration.exportFormat.name)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String?): T? =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    private companion object {
        const val PREFERENCES_NAME = "acquisition_configuration"
        const val KEY_STRATEGY = "strategy"
        const val KEY_MODEL = "model"
        const val KEY_RUNTIME = "runtime"
        const val KEY_FPS = "fps"
        const val KEY_VIDEO_QUALITY = "video_quality"
        const val KEY_COMPRESSION = "compression"
        const val KEY_SELECTED_PPE = "selected_ppe"
        const val KEY_SLIDING_WINDOW = "sliding_window"
        const val KEY_MAJORITY_VOTING = "majority_voting"
        const val KEY_EXPORT_FORMAT = "export_format"
    }
}
