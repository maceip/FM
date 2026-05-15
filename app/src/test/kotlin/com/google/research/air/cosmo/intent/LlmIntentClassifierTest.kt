package com.google.research.air.cosmo.intent

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.gatekeeping.GatekeepingResult
import com.google.research.air.cosmo.lib.llm.LLM
import com.google.research.air.cosmo.lib.llm.PromptPart
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LlmIntentClassifierTest {

    @Before
    fun stubAndroidLog() {
        // android.util.Log is unmocked in JVM unit tests; LlmIntentClassifier writes Log.w
        // on the LLM-failure and malformed-JSON paths. Stub the static methods to no-op so the
        // tests that exercise those paths don't crash on the platform stub.
        mockkStatic(Log::class)
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    @After
    fun resetAndroidLog() {
        unmockkStatic(Log::class)
    }

    @Test
    fun parsesFencedJsonResponseIntoIntentRecord() = runTest {
        val llm = FakeLlm("""
            Some preamble.
            ```json
            {"reasoning":"list request","final_answer": "Grocery; add_to_list", "slots": {"item": "eggs", "list": "grocery"}, "confidence": 0.92}
            ```
            Trailing notes.
        """.trimIndent())
        val classifier = classifier(llm)

        val record = classifier.classify(
            GatekeepingResult(
                detectedSkills = mapOf("add_to_list" to 0.91f),
                modelId = "bert",
                detectedBy = "bert",
                triggeredFulfillment = true,
                input = "add eggs to the grocery list"
            )
        )

        assertNotNull(record)
        assertEquals("add_to_list", record!!.intent)
        assertEquals("eggs", record.slots["item"])
        assertEquals("grocery", record.slots["list"])
        assertEquals(0.92f, record.confidence, 0.0001f)
        assertEquals("llm:bert", record.modelId)
    }

    @Test
    fun parsesBareJsonResponseWithoutFences() = runTest {
        val llm = FakeLlm("""{"reasoning":"timer request","final_answer":"Clock; set_timer","slots":{"duration":"10 minutes"},"confidence":0.88}""")
        val classifier = classifier(llm)

        val record = classifier.classify(
            GatekeepingResult(
                detectedSkills = mapOf("set_timer" to 0.9f),
                modelId = "bert",
                detectedBy = "bert",
                triggeredFulfillment = true,
                input = "set a timer for ten minutes"
            )
        )

        assertNotNull(record)
        assertEquals("set_timer", record!!.intent)
        assertEquals("10 minutes", record.slots["duration"])
    }

    @Test
    fun returnsNullWhenLlmThrows() = runTest {
        val llm = ThrowingLlm()
        val classifier = classifier(llm)

        val record = classifier.classify(
            GatekeepingResult(
                detectedSkills = mapOf("add_to_list" to 0.91f),
                modelId = "bert",
                detectedBy = "bert",
                triggeredFulfillment = true,
                input = "add eggs to the grocery list"
            )
        )

        assertNull(record)
    }

    @Test
    fun returnsNullWhenJsonIsMalformed() = runTest {
        val llm = FakeLlm("this is not json at all")
        val classifier = classifier(llm)

        val record = classifier.classify(
            GatekeepingResult(
                detectedSkills = mapOf("add_to_list" to 0.91f),
                modelId = "bert",
                detectedBy = "bert",
                triggeredFulfillment = true,
                input = "add eggs"
            )
        )

        assertNull(record)
    }

    @Test
    fun returnsNullWhenIntentIsUnknown() = runTest {
        val llm = FakeLlm("""{"reasoning":"unclear","final_answer":"unknown","slots":{},"confidence":0.1}""")
        val classifier = classifier(llm)

        val record = classifier.classify(
            GatekeepingResult(
                detectedSkills = mapOf("web_search" to 0.4f),
                modelId = "bert",
                detectedBy = "bert",
                triggeredFulfillment = false,
                input = "what's up"
            )
        )

        assertNull(record)
    }

    @Test
    fun returnsNullWhenInputAndDetectedSkillsAreEmpty() = runTest {
        // No detected skill + no triggeredFulfillment → don't waste an LLM call.
        val llm = FailingLlm()
        val classifier = classifier(llm)

        val record = classifier.classify(
            GatekeepingResult(
                detectedSkills = emptyMap(),
                modelId = "bert",
                detectedBy = "none",
                triggeredFulfillment = false,
                input = "ambient noise"
            )
        )

        assertNull(record)
        assertEquals(0, llm.callCount)
    }

    @Test
    fun clampsConfidenceToValidRange() = runTest {
        val llm = FakeLlm("""{"reasoning":"timer request","final_answer":"Clock; set_timer","slots":{},"confidence":2.5}""")
        val classifier = classifier(llm)

        val record = classifier.classify(
            GatekeepingResult(
                detectedSkills = mapOf("set_timer" to 0.8f),
                modelId = "bert",
                detectedBy = "bert",
                triggeredFulfillment = true,
                input = "set a timer"
            )
        )

        assertNotNull(record)
        assertEquals(1.0f, record!!.confidence, 0.0001f)
    }

    private class FakeLlm(private val response: String) : LLM {
        override suspend fun inference(prompt: String, onToken: (String) -> Unit): String {
            onToken(response)
            return response
        }

        override suspend fun inference(parts: List<PromptPart>, onToken: (String) -> Unit): String {
            onToken(response)
            return response
        }
    }

    private class ThrowingLlm : LLM {
        override suspend fun inference(prompt: String, onToken: (String) -> Unit): String {
            error("llm down")
        }

        override suspend fun inference(parts: List<PromptPart>, onToken: (String) -> Unit): String {
            error("llm down")
        }
    }

    private class FailingLlm : LLM {
        var callCount = 0

        override suspend fun inference(prompt: String, onToken: (String) -> Unit): String {
            callCount += 1
            throw AssertionError("LLM should not be invoked")
        }

        override suspend fun inference(parts: List<PromptPart>, onToken: (String) -> Unit): String {
            callCount += 1
            throw AssertionError("LLM should not be invoked")
        }
    }

    private fun classifier(llm: LLM): LlmIntentClassifier =
        LlmIntentClassifier(
            appContext = fallbackAssetContext(),
            llm = llm,
            ambientAudioBuffer = InMemoryEphemeralBuffer(16, 60_000, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            screenInteractionBuffer = InMemoryEphemeralBuffer(16, 60_000, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        )

    private fun fallbackAssetContext(): Context {
        val assets =
            mockk<AssetManager> {
                every { open(any()) } throws IOException("missing test asset")
            }
        return mockk {
            every { this@mockk.assets } returns assets
        }
    }
}
