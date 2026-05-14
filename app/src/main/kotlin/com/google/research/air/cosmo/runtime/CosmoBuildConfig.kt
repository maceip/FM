package com.google.research.air.cosmo.runtime

import com.google.research.air.cosmo.BuildConfig

object CosmoBuildConfig {
    const val isExternal: Boolean = false
    const val isTeamfood: Boolean = true
    val appVersionName: String = BuildConfig.VERSION_NAME
    val appVersionCode: Int = BuildConfig.VERSION_CODE
    val remoteInferenceBaseUrl: String = BuildConfig.REMOTE_INFERENCE_BASE_URL
    val remoteInferenceCertificatePin: String = BuildConfig.REMOTE_INFERENCE_CERT_PIN
    val remoteInferenceAudience: String = BuildConfig.REMOTE_INFERENCE_AUDIENCE
    val localLiteRtLmBackend: String = BuildConfig.LOCAL_LITERTLM_BACKEND
    val localLiteRtLmMaxTokens: Int = BuildConfig.LOCAL_LITERTLM_MAX_TOKENS
    val localLiteRtLmModelId: String = BuildConfig.LOCAL_LITERTLM_MODEL_ID
    val aicoreExperimentalNonPublic: Boolean = BuildConfig.AICORE_EXPERIMENTAL_NON_PUBLIC

    fun sodaPackUrl(kind: com.google.research.air.cosmo.lib.soda.SodaPackKind): String = when (kind) {
        com.google.research.air.cosmo.lib.soda.SodaPackKind.CPU -> BuildConfig.SODA_PACK_CPU_URL
        com.google.research.air.cosmo.lib.soda.SodaPackKind.LGA_TPU -> BuildConfig.SODA_PACK_LGA_TPU_URL
        com.google.research.air.cosmo.lib.soda.SodaPackKind.ZUMA_TPU -> BuildConfig.SODA_PACK_ZUMA_TPU_URL
    }

    fun sodaPackSha256(kind: com.google.research.air.cosmo.lib.soda.SodaPackKind): String = when (kind) {
        com.google.research.air.cosmo.lib.soda.SodaPackKind.CPU -> BuildConfig.SODA_PACK_CPU_SHA256
        com.google.research.air.cosmo.lib.soda.SodaPackKind.LGA_TPU -> BuildConfig.SODA_PACK_LGA_TPU_SHA256
        com.google.research.air.cosmo.lib.soda.SodaPackKind.ZUMA_TPU -> BuildConfig.SODA_PACK_ZUMA_TPU_SHA256
    }

    fun sodaPackSizeBytes(kind: com.google.research.air.cosmo.lib.soda.SodaPackKind): Long = when (kind) {
        com.google.research.air.cosmo.lib.soda.SodaPackKind.CPU -> BuildConfig.SODA_PACK_CPU_SIZE_BYTES
        com.google.research.air.cosmo.lib.soda.SodaPackKind.LGA_TPU -> BuildConfig.SODA_PACK_LGA_TPU_SIZE_BYTES
        com.google.research.air.cosmo.lib.soda.SodaPackKind.ZUMA_TPU -> BuildConfig.SODA_PACK_ZUMA_TPU_SIZE_BYTES
    }
}
