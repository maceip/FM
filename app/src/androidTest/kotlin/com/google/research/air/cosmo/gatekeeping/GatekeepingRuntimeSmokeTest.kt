package com.google.research.air.cosmo.gatekeeping

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.research.air.cosmo.gatekeeping.models.BertEncoderImpl
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.tensorflow.lite.Interpreter

class GatekeepingRuntimeSmokeTest {
    @Test
    fun bertCpuModelReturnsFiniteNineteenFloatVector() {
        assumeFalse(
            "LiteRT BERT JNI crashes inside libLiteRt.so on the arm64 emulator; run this smoke on physical devices.",
            NativeBertRuntimeGuard.isProbablyEmulator()
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loadAssetBytes = GatekeeperModelIds.byteLoaderFor(context)
        val vocab = loadAssetBytes.required(GatekeeperModelIds.BERT_VOCAB)
        val cpuModel = loadAssetBytes.required(GatekeeperModelIds.BERT_CPU)
        val handle =
            BertEncoderImpl.nativeCreate(
                vocab,
                cpuModel,
                isTpuEnabled = false
            )
        assertNotEquals("CPU BERT native handle should be created", 0L, handle)
        try {
            val output =
                checkNotNull(BertEncoderImpl.nativeRunStandardEncoder(handle, "Screen: define polymorphism", -1)) {
                    "CPU BERT output should be non-null"
                }
            assertEquals(19, output.size)
            assertTrue("CPU BERT output should be finite", output.all { it.isFinite() })
        } finally {
            BertEncoderImpl.nativeDestroy(handle)
        }
    }

    @Test
    fun inherentCpuModelRunsFrontendAndThirteenHeadBackend() = runBlocking {
        assumeFalse(
            "LiteRT JNI crashes inside libLiteRt.so on the arm64 emulator; run this smoke on physical devices.",
            NativeBertRuntimeGuard.isProbablyEmulator()
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = InherentAudioGatekeepingModel(context).predict(ByteArray(16_000 * 2))

        assertEquals("inherent_v0", result.modelId)
        assertTrue("Inherent result should expose only finite confidences", result.detectedSkills.values.all { it.isFinite() })
    }

    @Test
    fun recoveredEdgeTpuAudioModelDoesNotLoadThroughPublicNnApiPath() {
        assumeFalse(
            "NNAPI custom-op loading should be validated on physical devices.",
            NativeBertRuntimeGuard.isProbablyEmulator()
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelBytes = GatekeeperModelIds.byteLoaderFor(context).required(GatekeeperModelIds.AUDIO_GATEKEEPER)
        val modelBuffer =
            ByteBuffer.allocateDirect(modelBytes.size).order(ByteOrder.nativeOrder()).apply {
                put(modelBytes)
                rewind()
            }
        val failure =
            runCatching {
                Interpreter(modelBuffer, Interpreter.Options().setUseNNAPI(true)).use { interpreter ->
                    Log.i(TAG, "public NNAPI unexpectedly loaded recovered EdgeTPU model: inputs=${interpreter.inputTensorCount}")
                }
            }.exceptionOrNull()

        assertTrue("Recovered EdgeTPU custom-op model should not load through generic public NNAPI", failure != null)
        Log.i(TAG, "public NNAPI recovered EdgeTPU create failed: ${failure!!::class.java.name}: ${failure.message}")
    }

    private fun ((String) -> ByteArray?).required(id: String): ByteArray =
        invoke(id) ?: error("Required gatekeeper model $id unavailable")

    companion object {
        private const val TAG = "GatekeepingRuntimeSmoke"
    }
}
