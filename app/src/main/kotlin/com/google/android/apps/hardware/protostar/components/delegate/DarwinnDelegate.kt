package com.google.android.apps.hardware.protostar.components.delegate

import android.content.Context
import android.os.StrictMode
import org.tensorflow.lite.Delegate

/**
 * Minimal Java/Kotlin surface for Cosmo's recovered Darwinn delegate JNI library.
 *
 * The class name and native method signatures intentionally match the recovered smali so
 * `libdarwinndelegatejni.so` resolves its JNI entry points.
 */
class DarwinnDelegate(
    @Suppress("UNUSED_PARAMETER") private val context: Context,
    private val enableTachyonBackend: Boolean = false
) : Delegate {
    private var delegateHandle: Long =
        createDelegate(
            DARWINN_API2_POWER_STATE_ACTIVE,
            DARWINN_API2_MEMORY_POWER_STATE_MIN_POWER,
            enableTachyonBackend
        )

    override fun getNativeHandle(): Long = delegateHandle

    override fun close() {
        val handle = delegateHandle
        delegateHandle = 0L
        if (handle != 0L) {
            deleteDelegate(handle)
        }
    }

    companion object {
        const val DARWINN_DELEGATE_BINARY_NAME: String = "darwinndelegatejni"
        private const val DARWINN_API2_POWER_STATE_ACTIVE = 6
        private const val DARWINN_API2_MEMORY_POWER_STATE_MIN_POWER = 1

        init {
            loadNativeLibrary()
        }

        @JvmStatic
        external fun createDelegate(
            inferencePowerState: Int,
            inferenceMemoryPowerState: Int,
            useTachyonBackend: Boolean
        ): Long

        @JvmStatic
        external fun deleteDelegate(delegateHandle: Long)

        private fun loadNativeLibrary() {
            val previousPolicy = StrictMode.getThreadPolicy()
            try {
                StrictMode.setThreadPolicy(
                    StrictMode.ThreadPolicy.Builder(previousPolicy)
                        .permitDiskReads()
                        .build()
                )
                System.loadLibrary(DARWINN_DELEGATE_BINARY_NAME)
            } finally {
                StrictMode.setThreadPolicy(previousPolicy)
            }
        }
    }
}
