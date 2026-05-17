package com.google.research.air.cosmo.gatekeeping

import androidx.test.platform.app.InstrumentationRegistry
import com.google.research.air.cosmo.gatekeeping.models.BertEncoderImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test

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

    private fun ((String) -> ByteArray?).required(id: String): ByteArray =
        invoke(id) ?: error("Required gatekeeper model $id unavailable")
}
