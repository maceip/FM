package com.google.research.air.cosmo.gatekeeping

import android.content.Context
import android.os.Build
import com.google.research.air.cosmo.gatekeeping.models.BertEncoderImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backend SoC classification, used only by [BertClassifierGatekeepingModel] to filter the
 * compile-time [BACKENDS] table. Kept as an enum (load-bearing for the table); the previous
 * `SocResolver` interface + `DefaultSocResolver` object indirection was deleted per architecture-
 * delta C4 row 9 — `Build.SOC_MANUFACTURER` is read directly at the single call site.
 */
enum class SocClass {
    GOOGLE_TENSOR,
    MEDIATEK,
    OTHER;

    companion object {
        fun current(): SocClass =
            when (Build.SOC_MANUFACTURER.orEmpty().lowercase()) {
                "google" -> GOOGLE_TENSOR
                "mediatek" -> MEDIATEK
                else -> OTHER
            }
    }
}

@Singleton
class BertClassifierGatekeepingModel internal constructor(
    // Test seam: each model id maps to a bundled asset filename via `GatekeeperModelIds`. The
    // lambda returns asset bytes or null when the file is missing. Production binds to
    // `GatekeeperModelIds.byteLoaderFor(context)`; tests pass `{ id -> fakeAssets[id] }`.
    // The runtime JSON-manifest indirection (ManifestGatekeepingModelLoader) was deleted per
    // architecture-delta C4 row 5; `scripts/download-models.sh --verify-only` is the CI gate.
    private val loadAssetBytes: (String) -> ByteArray?,
    // Test seam: overrides the SoC class so the backend-filter logic can be exercised across
    // GOOGLE_TENSOR/MEDIATEK/OTHER without touching `Build.SOC_MANUFACTURER`. Production passes
    // `SocClass.Companion::current`, which inlines the `Build.SOC_MANUFACTURER` read.
    private val socProvider: () -> SocClass,
    private val nativeRuntimeSupported: () -> Boolean = { true },
    private val nativeCreate: (ByteArray, ByteArray, Boolean) -> Long,
    private val nativeRun: (Long, String, Int) -> FloatArray?,
    private val nativeDestroy: (Long) -> Unit
) : GatekeepingModel {
    @Inject
    constructor(
        @ApplicationContext context: Context
    ) : this(
        loadAssetBytes = GatekeeperModelIds.byteLoaderFor(context),
        socProvider = SocClass.Companion::current,
        nativeRuntimeSupported = NativeBertRuntimeGuard::isSupportedOnThisDevice,
        nativeCreate = { vocabBuffer, modelBuffer, isTpuEnabled ->
            BertEncoderImpl.nativeCreate(vocabBuffer, modelBuffer, isTpuEnabled)
        },
        nativeRun = { handle, query, segmentIdOverride ->
            BertEncoderImpl.nativeRunStandardEncoder(handle, query, segmentIdOverride)
        },
        nativeDestroy = { handle -> BertEncoderImpl.nativeDestroy(handle) }
    )

    @Volatile private var handle: Long = 0L
    @Volatile private var unavailableReason: String? = null

    override suspend fun predict(input: String): GatekeepingResult {
        val source = GatekeepingInputSource.fromInput(input)
        val profile = ThresholdProfile.forSource(source)
        val scores = runBert(input)
        val skills = scores?.let { mapBertOutputToSkills(it, profile) }.orEmpty()
        val screenSignals = scores?.let { mapScreenSignals(it, profile) }.orEmpty()
        return GatekeepingResult(
            detectedSkills = skills,
            modelId = scores?.let { "bert" } ?: "bert_unavailable:${unavailableReason ?: "bert_cpu_unavailable"}",
            detectedBy = if (skills.isEmpty()) "none" else "bert",
            triggeredFulfillment = skills.isNotEmpty(),
            input = input,
            inputSource = source,
            detector = if (skills.isEmpty()) GatekeepingDetector.NONE else GatekeepingDetector.BERT,
            screenSignals = screenSignals
        )
    }

    override suspend fun predict(pcm: ByteArray): GatekeepingResult =
        throw UnsupportedOperationException("BERT gatekeeper does not support raw audio prediction")

    override fun close() {
        val current = handle
        handle = 0L
        if (current != 0L) {
            runCatching { nativeDestroy(current) }
        }
    }

    private fun runBert(input: String): FloatArray? {
        val currentHandle = ensureHandle()
        if (currentHandle == 0L) return null
        val output =
            runCatching { nativeRun(currentHandle, input, DEFAULT_SEGMENT_ID_OVERRIDE) }
                .getOrElse {
                    unavailableReason = "bert_native_run_failed:${it.javaClass.simpleName}"
                    return null
                }
        if (output == null || output.size != OUTPUT_SIZE || output.any { !it.isFinite() }) {
            unavailableReason = "bert_native_invalid_output"
            return null
        }
        return output
    }

    private fun ensureHandle(): Long {
        if (handle != 0L || unavailableReason != null) return handle
        if (!nativeRuntimeSupported()) {
            unavailableReason = "bert_native_unsupported_on_emulator"
            return 0L
        }
        val vocabBytes = loadAssetBytes(GatekeeperModelIds.BERT_VOCAB)
        if (vocabBytes == null) {
            unavailableReason = "bert_vocab_unavailable:${GatekeeperModelIds.BERT_VOCAB}"
            return 0L
        }
        val socClass = socProvider()
        val failures = mutableListOf<String>()
        for (backend in BACKENDS.filter { socClass in it.supportedSocs }) {
            val modelBytes = loadAssetBytes(backend.id)
            if (modelBytes == null) {
                failures += "bert_model_unavailable:${backend.id}"
                continue
            }
            val created =
                runCatching { nativeCreate(vocabBytes, modelBytes, backend.tpu) }
                    .getOrElse {
                        failures += "bert_native_create_failed:${backend.id}:${it.javaClass.simpleName}"
                        0L
                    }
            if (created != 0L) {
                handle = created
                return created
            }
            failures += "bert_native_create_returned_zero:${backend.id}"
        }
        unavailableReason = "bert_all_backends_unavailable:${socClass}:${failures.joinToString("|")}"
        return 0L
    }

    private fun mapBertOutputToSkills(
        scores: FloatArray,
        profile: ThresholdProfile
    ): Map<String, Float> {
        val thresholds = thresholdsFor(profile)
        return INDEX_TO_SKILLS.entries
            .filter { (index, _) -> index < scores.size && scores[index] >= thresholds[index] }
            .flatMap { (index, skills) -> skills.map { it to scores[index] } }
            .toMap()
    }

    private fun mapScreenSignals(
        scores: FloatArray,
        profile: ThresholdProfile
    ): Map<String, Float> {
        val thresholds = thresholdsFor(profile)
        return INDEX_TO_SCREEN_SIGNALS.entries
            .filter { (index, _) -> index < scores.size && scores[index] >= thresholds[index] }
            .associate { (index, label) -> label to scores[index] }
    }

    private data class Backend(
        val id: String,
        val tpu: Boolean,
        val supportedSocs: Set<SocClass>
    )

    private enum class ThresholdProfile {
        DEFAULT,
        SCREEN,
        NOTIFICATION,
        AUDIO_DEFAULT,
        AUDIO_USER,
        AUDIO_NON_USER;

        companion object {
            fun forSource(source: GatekeepingInputSource): ThresholdProfile =
                when (source) {
                    GatekeepingInputSource.SCREEN, GatekeepingInputSource.SCREEN_INTERACTION -> SCREEN
                    GatekeepingInputSource.NOTIFICATION -> NOTIFICATION
                    GatekeepingInputSource.USER_QUERY -> AUDIO_USER
                    GatekeepingInputSource.AMBIENT_AUDIO -> AUDIO_NON_USER
                    GatekeepingInputSource.BUDS_AUDIO,
                    GatekeepingInputSource.DEVICE_AUDIO,
                    GatekeepingInputSource.WATCH_AUDIO -> AUDIO_DEFAULT
                    else -> DEFAULT
                }
        }
    }

    private companion object {
        const val OUTPUT_SIZE = 19
        private const val DEFAULT_SEGMENT_ID_OVERRIDE = -1
        private const val LEGACY_DEFAULT_THRESHOLD = 0.5f

        val BACKENDS =
            listOf(
                Backend(GatekeeperModelIds.BERT_EDGETPU_P25, tpu = true, supportedSocs = setOf(SocClass.GOOGLE_TENSOR)),
                Backend(GatekeeperModelIds.BERT_EDGETPU_P24, tpu = true, supportedSocs = setOf(SocClass.GOOGLE_TENSOR)),
                Backend(GatekeeperModelIds.BERT_NPU_MT6989, tpu = true, supportedSocs = setOf(SocClass.MEDIATEK)),
                Backend(GatekeeperModelIds.BERT_CPU, tpu = false, supportedSocs = setOf(SocClass.GOOGLE_TENSOR, SocClass.MEDIATEK, SocClass.OTHER))
            )

        val INDEX_TO_SKILLS =
            mapOf(
                0 to listOf("add_to_memory"),
                1 to listOf("add_to_list"),
                2 to listOf("define_term", "web_search"),
                3 to listOf("query_photos"),
                4 to listOf("create_calendar_event"),
                5 to listOf("create_doc"),
                6 to listOf("provide_person_context"),
                7 to listOf("provide_event_context"),
                8 to listOf("deep_research"),
                9 to listOf("provide_insight"),
                10 to listOf("browsing_agent"),
                11 to listOf("call_agent"),
                12 to listOf("add_timer")
            )

        val INDEX_TO_SCREEN_SIGNALS =
            mapOf(
                13 to "is_shopping_cart",
                14 to "is_checkout_page",
                15 to "is_product_details",
                16 to "is_search_results",
                17 to "is_travel_category",
                18 to "is_shopping_category"
            )

        private val SCREEN =
            floatArrayOf(
                0.169f, 0.192f, 0.10f, 0.10f, 0.17f, 0.10f, 0.10f, 0.10f, 0.18f, 0.10f,
                0.10f, 0.10f, 0.194f, 0.20f, 0.20f, 0.20f, 0.20f, 0.20f, 0.20f
            )

        private val NOTIFICATION =
            floatArrayOf(
                0.169f, 0.148f, 0.10f, 0.189f, 0.202f, 0.10f, 0.5f, 0.10f, 0.108f, 0.10f,
                0.14f, 0.415f, 0.405f, 0.105f, 0.20f, 0.20f, 0.20f, 0.20f, 0.20f
            )

        private val AUDIO_DEFAULT =
            floatArrayOf(
                0.169f, 0.192f, 0.413f, 0.198f, 0.119f, 0.526f, 0.10f, 0.551f, 0.15f, 0.385f,
                0.10f, 0.10f, 0.10f, 0.10f, 0.10f, 0.20f, 0.20f, 0.20f, 0.20f
            )

        private val AUDIO_USER =
            floatArrayOf(
                0.169f, 0.192f, 0.413f, 0.198f, 0.119f, 0.526f, 0.10f, 0.551f, 0.15f, 0.385f,
                0.10f, 0.10f, 0.10f, 0.10f, 0.10f, 0.10f, 0.20f, 0.20f, 0.20f
            )

        private val AUDIO_NON_USER =
            floatArrayOf(
                0.169f, 0.192f, 0.10f, 0.10f, 0.493f, 0.366f, 0.10f, 0.787f, 0.25f, 0.551f,
                0.388f, 0.385f, 0.10f, 0.10f, 0.203f, 0.10f, 0.103f, 0.20f, 0.20f
            )

        private val DEFAULT_PROFILE = FloatArray(OUTPUT_SIZE) { LEGACY_DEFAULT_THRESHOLD }

        private fun thresholdsFor(profile: ThresholdProfile): FloatArray =
            when (profile) {
                ThresholdProfile.SCREEN -> SCREEN
                ThresholdProfile.NOTIFICATION -> NOTIFICATION
                ThresholdProfile.AUDIO_DEFAULT -> AUDIO_DEFAULT
                ThresholdProfile.AUDIO_USER -> AUDIO_USER
                ThresholdProfile.AUDIO_NON_USER -> AUDIO_NON_USER
                ThresholdProfile.DEFAULT -> DEFAULT_PROFILE
            }
    }
}

internal object NativeBertRuntimeGuard {
    fun isSupportedOnThisDevice(): Boolean = !isProbablyEmulator()

    fun isProbablyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.orEmpty().lowercase()
        val model = Build.MODEL.orEmpty().lowercase()
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        val device = Build.DEVICE.orEmpty().lowercase()
        val product = Build.PRODUCT.orEmpty().lowercase()
        val hardware = Build.HARDWARE.orEmpty().lowercase()
        return fingerprint.startsWith("generic") ||
            fingerprint.contains("sdk_gphone") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            manufacturer.contains("genymotion") ||
            hardware == "goldfish" ||
            hardware == "ranchu" ||
            brand.startsWith("generic") && device.startsWith("generic") ||
            product.contains("sdk")
    }
}

class CombinedBertRegexGatekeepingModel
@Inject
constructor(
    @RegexGatekeeper private val regex: GatekeepingModel,
    @BertGatekeeper private val bert: GatekeepingModel
) : GatekeepingModel {
    override suspend fun predict(input: String): GatekeepingResult {
        val regexResult = regex.predict(input)
        val bertResult = bert.predict(input)
        val fused =
            (regexResult.detectedSkills.keys + bertResult.detectedSkills.keys).associateWith { key ->
                maxOf(regexResult.detectedSkills[key] ?: 0f, bertResult.detectedSkills[key] ?: 0f)
            }
        val detectedBy =
            when {
                regexResult.detectedSkills.isNotEmpty() && bertResult.detectedSkills.isNotEmpty() -> "both"
                regexResult.detectedSkills.isNotEmpty() -> "regex"
                bertResult.detectedSkills.isNotEmpty() -> "bert"
                else -> "none"
            }
        return GatekeepingResult(
            detectedSkills = fused,
            modelId = "combined",
            detectedBy = detectedBy,
            triggeredFulfillment = fused.isNotEmpty(),
            input = input,
            inputSource = GatekeepingInputSource.fromInput(input),
            detector = GatekeepingDetector.fromDetectedBy(detectedBy),
            screenSignals = bertResult.screenSignals
        )
    }
}
