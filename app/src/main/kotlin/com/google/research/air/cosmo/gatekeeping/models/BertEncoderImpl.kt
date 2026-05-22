package com.google.research.air.cosmo.gatekeeping.models

/**
 * Binary-compatible entry point for the recovered BERT gatekeeper JNI surface.
 */
class BertEncoderImpl private constructor() {
    companion object {
        init {
            System.loadLibrary("bert_gatekeeper_jni")
        }

        @JvmStatic
        external fun nativeCreate(
            vocabBuffer: ByteArray,
            modelBuffer: ByteArray,
            isTpuEnabled: Boolean,
            dispatchLibraryDir: String?
        ): Long

        @JvmStatic
        external fun nativeRunStandardEncoder(
            handle: Long,
            query: String,
            segmentIdOverride: Int
        ): FloatArray?

        @JvmStatic
        external fun nativeRunMultiEncoder(
            handle: Long,
            query: String,
            segmentIdOverride: Int
        ): BertEncoderOutput?

        @JvmStatic
        external fun nativeDebugBuildInputs(
            handle: Long,
            query: String,
            segmentIdOverride: Int
        ): IntArray?

        @JvmStatic
        external fun nativeDestroy(handle: Long)
    }
}
