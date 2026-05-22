package com.google.research.air.cosmo.gatekeeping

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.research.air.cosmo.gatekeeping.models.AudioGatekeeperLiteRtImpl
import com.google.research.air.cosmo.gatekeeping.models.BertEncoderImpl
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
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
                isTpuEnabled = false,
                dispatchLibraryDir = null
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
    fun bertGoogleTensorG5ModelReturnsFiniteNineteenFloatVector() {
        assumeFalse(
            "LiteRT BERT JNI crashes inside libLiteRt.so on the arm64 emulator; run this smoke on physical devices.",
            NativeBertRuntimeGuard.isProbablyEmulator()
        )
        assumeTrue("Google Tensor G5 AOT model is only validated on Google Tensor devices.", SocClass.current() == SocClass.GOOGLE_TENSOR)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loadAssetBytes = GatekeeperModelIds.byteLoaderFor(context)
        val vocab = loadAssetBytes.required(GatekeeperModelIds.BERT_VOCAB)
        val g5Model = loadAssetBytes.required(GatekeeperModelIds.BERT_GOOGLE_TENSOR_G5)
        val handle =
            BertEncoderImpl.nativeCreate(
                vocab,
                g5Model,
                isTpuEnabled = true,
                dispatchLibraryDir = context.applicationInfo.nativeLibraryDir
            )
        assertNotEquals("Google Tensor G5 BERT native handle should be created", 0L, handle)
        try {
            val output =
                checkNotNull(BertEncoderImpl.nativeRunStandardEncoder(handle, "Screen: define polymorphism", -1)) {
                    "Google Tensor G5 BERT output should be non-null"
                }
            assertEquals(19, output.size)
            assertTrue("Google Tensor G5 BERT output should be finite", output.all { it.isFinite() })
        } finally {
            BertEncoderImpl.nativeDestroy(handle)
        }
    }

    @Test
    fun inherentModelRunsFrontendAndThirteenHeadBackend() = runBlocking {
        assumeFalse(
            "LiteRT JNI crashes inside libLiteRt.so on the arm64 emulator; run this smoke on physical devices.",
            NativeBertRuntimeGuard.isProbablyEmulator()
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = InherentAudioGatekeepingModel(context).predict(ByteArray(16_000 * 2))

        assertTrue(
            "Inherent result should use the Google Tensor G5 backend or the CPU fallback",
            result.modelId == InherentAudioGatekeepingModel.MODEL_ID_GOOGLE_TENSOR_G5 ||
                result.modelId == InherentAudioGatekeepingModel.MODEL_ID_CPU
        )
        assertTrue("Inherent result should expose only finite confidences", result.detectedSkills.values.all { it.isFinite() })
    }

    @Test
    fun inherentGoogleTensorG5BackendRunsThroughLiteRtNpu() {
        assumeFalse(
            "LiteRT JNI crashes inside libLiteRt.so on the arm64 emulator; run this smoke on physical devices.",
            NativeBertRuntimeGuard.isProbablyEmulator()
        )
        assumeTrue("Google Tensor G5 AOT model is only validated on Google Tensor devices.", SocClass.current() == SocClass.GOOGLE_TENSOR)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val g5Model = GatekeeperModelIds.byteLoaderFor(context).required(GatekeeperModelIds.INHERENT_V0_GOOGLE_TENSOR_G5)
        val handle =
            AudioGatekeeperLiteRtImpl.nativeCreate(
                g5Model,
                useNpu = true,
                expectedInputShape = intArrayOf(
                    1,
                    InherentAudioGatekeepingModel.MAX_FRAMES,
                    InherentAudioGatekeepingModel.MEL_BINS
                ),
                expectedOutputShape = intArrayOf(1, InherentAudioGatekeepingModel.NUM_INTENTS),
                dispatchLibraryDir = context.applicationInfo.nativeLibraryDir
            )
        assertNotEquals("Google Tensor G5 inherent backend handle should be created", 0L, handle)
        try {
            val input =
                ByteBuffer
                    .allocateDirect(
                        InherentAudioGatekeepingModel.MAX_FRAMES *
                            InherentAudioGatekeepingModel.MEL_BINS *
                            Float.SIZE_BYTES
                    )
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
            val output =
                ByteBuffer
                    .allocateDirect(InherentAudioGatekeepingModel.NUM_INTENTS * Float.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
            assertTrue("Google Tensor G5 inherent backend should run", AudioGatekeeperLiteRtImpl.nativeRun(handle, input, output))
            output.position(0)
            val scores = FloatArray(InherentAudioGatekeepingModel.NUM_INTENTS) { output.get() }
            assertTrue("Google Tensor G5 inherent output should be finite", scores.all { it.isFinite() })
        } finally {
            AudioGatekeeperLiteRtImpl.nativeDestroy(handle)
        }
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
