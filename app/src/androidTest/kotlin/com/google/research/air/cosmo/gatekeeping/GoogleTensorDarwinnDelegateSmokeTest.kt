package com.google.research.air.cosmo.gatekeeping

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.apps.hardware.protostar.components.delegate.DarwinnDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.tensorflow.lite.Interpreter

class GoogleTensorDarwinnDelegateSmokeTest {
    @Test
    fun bertGoogleTensorG5RunsThroughDarwinnDelegate() {
        assumePhysicalGoogleTensorDevice()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = directModelBuffer(GatekeeperModelIds.BERT_GOOGLE_TENSOR_G5)
        DarwinnDelegate(context, enableTachyonBackend = false).use { delegate ->
            assertNotEquals("Darwinn delegate handle should be non-zero", 0L, delegate.nativeHandle)
            Interpreter(model, Interpreter.Options().addDelegate(delegate)).use { interpreter ->
                assertEquals(3, interpreter.inputTensorCount)
                assertEquals(1, interpreter.outputTensorCount)
                val wordIds = arrayOf(IntArray(BERT_SEQUENCE_LENGTH))
                val typeIds = arrayOf(IntArray(BERT_SEQUENCE_LENGTH))
                val mask = arrayOf(IntArray(BERT_SEQUENCE_LENGTH) { 1 })
                val output = arrayOf(FloatArray(BERT_OUTPUT_SIZE))
                interpreter.runForMultipleInputsOutputs(
                    arrayOf(wordIds, typeIds, mask),
                    mapOf(0 to output)
                )
                assertEquals(BERT_OUTPUT_SIZE, output[0].size)
                assertTrue("Google Tensor G5 BERT output should be finite", output[0].all { it.isFinite() })
            }
        }
    }

    @Test
    fun inherentGoogleTensorG5RunsThroughDarwinnDelegate() {
        assumePhysicalGoogleTensorDevice()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = directModelBuffer(GatekeeperModelIds.INHERENT_V0_GOOGLE_TENSOR_G5)
        DarwinnDelegate(context, enableTachyonBackend = false).use { delegate ->
            assertNotEquals("Darwinn delegate handle should be non-zero", 0L, delegate.nativeHandle)
            Interpreter(model, Interpreter.Options().addDelegate(delegate)).use { interpreter ->
                assertEquals(1, interpreter.inputTensorCount)
                assertEquals(1, interpreter.outputTensorCount)
                val input =
                    ByteBuffer
                        .allocateDirect(
                            InherentAudioGatekeepingModel.MAX_FRAMES *
                                InherentAudioGatekeepingModel.MEL_BINS *
                                Float.SIZE_BYTES
                        )
                        .order(ByteOrder.nativeOrder())
                val output = Array(1) { FloatArray(InherentAudioGatekeepingModel.NUM_INTENTS) }
                interpreter.run(input, output)
                assertTrue(
                    "Google Tensor G5 inherent output should be finite",
                    output[0].all { it.isFinite() }
                )
            }
        }
    }

    private fun assumePhysicalGoogleTensorDevice() {
        assumeFalse(
            "Darwinn delegate smoke must run on physical Pixel devices.",
            NativeBertRuntimeGuard.isProbablyEmulator()
        )
        assumeTrue(
            "Google Tensor G5 models are only validated on Google Tensor devices.",
            SocClass.current() == SocClass.GOOGLE_TENSOR
        )
    }

    private fun directModelBuffer(id: String): ByteBuffer {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = GatekeeperModelIds.filenameFor(id) ?: error("No filename for $id")
        val bytes = context.assets.open("models/$filename").use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes)
            rewind()
        }
    }

    private companion object {
        const val BERT_SEQUENCE_LENGTH = 512
        const val BERT_OUTPUT_SIZE = 19
    }
}
