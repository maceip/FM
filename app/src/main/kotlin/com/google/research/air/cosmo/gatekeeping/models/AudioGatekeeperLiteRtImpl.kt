package com.google.research.air.cosmo.gatekeeping.models

import java.nio.FloatBuffer

/**
 * JNI entry point for the raw-audio gatekeeper LiteRT frontend/backend models.
 */
class AudioGatekeeperLiteRtImpl private constructor() {
    companion object {
        init {
            System.loadLibrary("audio_gatekeeper_jni")
        }

        @JvmStatic
        external fun nativeCreate(
            modelBuffer: ByteArray,
            useNpu: Boolean
        ): Long

        @JvmStatic
        external fun nativeRun(
            handle: Long,
            input: FloatBuffer,
            output: FloatBuffer
        ): Boolean

        @JvmStatic
        external fun nativeDestroy(handle: Long)
    }
}
