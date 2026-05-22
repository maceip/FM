package com.google.research.air.cosmo.gatekeeping

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.research.air.cosmo.gatekeeping.models.AudioGatekeeperLiteRtImpl
import com.google.research.air.cosmo.gatekeeping.models.BertEncoderImpl
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.roundToLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.tensorflow.lite.Interpreter

class GatekeepingRuntimeBenchmarkTest {
    @Test
    fun benchmarkCpuAndGoogleTensorG5Gatekeepers() {
        assumeFalse(
            "LiteRT JNI crashes inside libLiteRt.so on the arm64 emulator; run this benchmark on physical devices.",
            NativeBertRuntimeGuard.isProbablyEmulator()
        )
        assumeTrue("Google Tensor G5 benchmark is only valid on Google Tensor devices.", SocClass.current() == SocClass.GOOGLE_TENSOR)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loadAssetBytes = GatekeeperModelIds.byteLoaderFor(context)
        val vocab = loadAssetBytes.required(GatekeeperModelIds.BERT_VOCAB)
        val bertCpu = loadAssetBytes.required(GatekeeperModelIds.BERT_CPU)
        val bertG5 = loadAssetBytes.required(GatekeeperModelIds.BERT_GOOGLE_TENSOR_G5)
        val inherentCpu = loadAssetBytes.required(GatekeeperModelIds.INHERENT_V0)
        val inherentG5 = loadAssetBytes.required(GatekeeperModelIds.INHERENT_V0_GOOGLE_TENSOR_G5)
        val dispatchLibraryDir = context.applicationInfo.nativeLibraryDir

        val results =
            listOf(
                benchmarkBert("bert_cpu_fallback", vocab, bertCpu, false, null),
                benchmarkBert("bert_tensor_g5", vocab, bertG5, true, dispatchLibraryDir),
                benchmarkInherentCpu("inherent_cpu_fallback", inherentCpu),
                benchmarkInherentG5("inherent_tensor_g5", inherentG5, dispatchLibraryDir)
            )
        val json =
            buildString {
                append("{")
                append("\"device\":\"").append(escape(Build.MODEL.orEmpty())).append("\",")
                append("\"soc\":\"").append(escape(Build.SOC_MODEL.orEmpty())).append("\",")
                append("\"deviceCode\":\"").append(escape(Build.DEVICE.orEmpty())).append("\",")
                append("\"timestampMs\":").append(System.currentTimeMillis()).append(",")
                append("\"results\":[")
                append(results.joinToString(",") { it.toJson() })
                append("]}")
            }
        Log.i(TAG, "BENCH_JSON $json")
        println("BENCH_JSON $json")
    }

    private fun benchmarkBert(
        name: String,
        vocab: ByteArray,
        model: ByteArray,
        useNpu: Boolean,
        dispatchLibraryDir: String?
    ): BenchResult {
        val handle = BertEncoderImpl.nativeCreate(vocab, model, useNpu, dispatchLibraryDir)
        assertNotEquals("$name handle should be created", 0L, handle)
        try {
            return benchmark(name) {
                val output =
                    checkNotNull(BertEncoderImpl.nativeRunStandardEncoder(handle, BERT_QUERY, -1)) {
                        "$name output should be non-null"
                    }
                assertEquals(19, output.size)
                assertTrue("$name output should be finite", output.all { it.isFinite() })
            }
        } finally {
            BertEncoderImpl.nativeDestroy(handle)
        }
    }

    private fun benchmarkInherentCpu(
        name: String,
        modelBytes: ByteArray
    ): BenchResult {
        val modelBuffer =
            ByteBuffer
                .allocateDirect(modelBytes.size)
                .order(ByteOrder.nativeOrder())
                .also { buffer ->
                    buffer.put(modelBytes)
                    buffer.position(0)
                }
        val interpreter = Interpreter(modelBuffer, Interpreter.Options())
        try {
            assertEquals(1, interpreter.inputTensorCount)
            assertEquals(1, interpreter.outputTensorCount)
            val input = newInherentInput()
            val output = newInherentOutput()
            return benchmark(name) {
                input.position(0)
                output.position(0)
                interpreter.run(input, output)
                output.position(0)
                val scores = FloatArray(InherentAudioGatekeepingModel.NUM_INTENTS) { output.get() }
                assertTrue("$name output should be finite", scores.all { it.isFinite() })
            }
        } finally {
            interpreter.close()
        }
    }

    private fun benchmarkInherentG5(
        name: String,
        modelBytes: ByteArray,
        dispatchLibraryDir: String
    ): BenchResult {
        val handle =
            AudioGatekeeperLiteRtImpl.nativeCreate(
                modelBytes,
                useNpu = true,
                expectedInputShape = intArrayOf(
                    1,
                    InherentAudioGatekeepingModel.MAX_FRAMES,
                    InherentAudioGatekeepingModel.MEL_BINS
                ),
                expectedOutputShape = intArrayOf(1, InherentAudioGatekeepingModel.NUM_INTENTS),
                dispatchLibraryDir = dispatchLibraryDir
            )
        assertNotEquals("$name handle should be created", 0L, handle)
        try {
            val input = newInherentInput()
            val output = newInherentOutput()
            return benchmark(name) {
                input.position(0)
                output.position(0)
                assertTrue("$name should run", AudioGatekeeperLiteRtImpl.nativeRun(handle, input, output))
                output.position(0)
                val scores = FloatArray(InherentAudioGatekeepingModel.NUM_INTENTS) { output.get() }
                assertTrue("$name output should be finite", scores.all { it.isFinite() })
            }
        } finally {
            AudioGatekeeperLiteRtImpl.nativeDestroy(handle)
        }
    }

    private fun benchmark(
        name: String,
        block: () -> Unit
    ): BenchResult {
        repeat(WARMUP_ITERATIONS) { block() }
        val samples = LongArray(MEASURED_ITERATIONS)
        repeat(MEASURED_ITERATIONS) { index ->
            val start = SystemClock.elapsedRealtimeNanos()
            block()
            samples[index] = SystemClock.elapsedRealtimeNanos() - start
        }
        return BenchResult(name, samples.map { it / 1_000_000.0 }.sorted())
    }

    private fun newInherentInput() =
        ByteBuffer
            .allocateDirect(
                InherentAudioGatekeepingModel.MAX_FRAMES *
                    InherentAudioGatekeepingModel.MEL_BINS *
                    Float.SIZE_BYTES
            )
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    private fun newInherentOutput() =
        ByteBuffer
            .allocateDirect(InherentAudioGatekeepingModel.NUM_INTENTS * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    private fun ((String) -> ByteArray?).required(id: String): ByteArray =
        invoke(id) ?: error("Required gatekeeper model $id unavailable")

    private data class BenchResult(
        val name: String,
        val sortedMs: List<Double>
    ) {
        private val mean = sortedMs.sum() / sortedMs.size
        private val median = percentile(0.50)
        private val p95 = percentile(0.95)

        fun toJson(): String =
            "{" +
                "\"name\":\"${escape(name)}\"," +
                "\"iterations\":${sortedMs.size}," +
                "\"meanMs\":${mean.round(3)}," +
                "\"medianMs\":${median.round(3)}," +
                "\"p95Ms\":${p95.round(3)}," +
                "\"minMs\":${sortedMs.first().round(3)}," +
                "\"maxMs\":${sortedMs.last().round(3)}" +
                "}"

        private fun percentile(p: Double): Double {
            val index = ((sortedMs.size - 1) * p).roundToLong().toInt().coerceIn(sortedMs.indices)
            return sortedMs[index]
        }
    }

    companion object {
        private const val TAG = "GatekeepingBenchmark"
        private const val BERT_QUERY = "Screen: define polymorphism"
        private const val WARMUP_ITERATIONS = 3
        private const val MEASURED_ITERATIONS = 20

        private fun Double.round(places: Int): String = String.format(Locale.US, "%.${places}f", this)

        private fun escape(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
