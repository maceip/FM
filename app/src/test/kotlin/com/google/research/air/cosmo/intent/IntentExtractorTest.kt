package com.google.research.air.cosmo.intent

import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.gatekeeping.GatekeeperOutputSource
import com.google.research.air.cosmo.gatekeeping.GatekeepingResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IntentExtractorTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)

    @Test
    fun startSubscribesAndWritesIntentRecordToBuffer() = runTest {
        val gatekeeper = FakeGatekeeperOutputSource()
        val buffer = InMemoryEphemeralBuffer<Any>(16, 60_000, clock)
        val classifier =
            CannedIntentClassifier(
                IntentRecord(
                    intent = "add_to_list",
                    slots = mapOf("item" to "eggs", "list" to "grocery"),
                    confidence = 0.91f,
                    input = "add eggs to the grocery list",
                    modelId = "llm:bert",
                )
            )
        val extractor = IntentExtractorImpl(
            gatekeeperOutputSource = gatekeeper,
            intentBuffer = buffer,
            classifier = classifier,
            scope = this
        )
        extractor.start()
        advanceUntilIdle()

        gatekeeper.emit(
            GatekeepingResult(
                detectedSkills = mapOf("add_to_list" to 0.91f, "web_search" to 0.42f),
                modelId = "bert",
                detectedBy = "bert",
                triggeredFulfillment = true,
                input = "add eggs to the grocery list"
            )
        )
        advanceUntilIdle()

        val record = buffer.snapshot().single().value as IntentRecord
        assertEquals("add_to_list", record.intent)
        assertEquals("eggs", record.slots["item"])
        assertEquals("grocery", record.slots["list"])
        assertEquals(0.91f, record.confidence, 0.0001f)
        assertEquals("llm:bert", record.modelId)

        extractor.close()
    }

    @Test
    fun classifierMayDeclineToEmitWhenNoIntentExtracted() = runTest {
        val gatekeeper = FakeGatekeeperOutputSource()
        val buffer = InMemoryEphemeralBuffer<Any>(16, 60_000, clock)
        val extractor = IntentExtractorImpl(
            gatekeeperOutputSource = gatekeeper,
            intentBuffer = buffer,
            classifier = CannedIntentClassifier(null),
            scope = this
        )
        extractor.start()
        advanceUntilIdle()

        gatekeeper.emit(
            GatekeepingResult(
                detectedSkills = emptyMap(),
                modelId = "bert",
                detectedBy = "none",
                triggeredFulfillment = false,
                input = "ambient noise"
            )
        )
        advanceUntilIdle()

        assertEquals(0, buffer.snapshot().size)
        extractor.close()
    }

    @Test
    fun closeStopsCollectingFutureEmissions() = runTest {
        val gatekeeper = FakeGatekeeperOutputSource()
        val buffer = InMemoryEphemeralBuffer<Any>(16, 60_000, clock)
        val extractor = IntentExtractorImpl(
            gatekeeperOutputSource = gatekeeper,
            intentBuffer = buffer,
            classifier = CannedIntentClassifier(
                IntentRecord("set_timer", emptyMap(), 0.8f, "set a timer", "test")
            ),
            scope = this
        )
        extractor.start()
        advanceUntilIdle()
        extractor.close()
        advanceUntilIdle()

        gatekeeper.emit(
            GatekeepingResult(
                detectedSkills = mapOf("add_timer" to 0.8f),
                modelId = "bert",
                detectedBy = "bert",
                triggeredFulfillment = true,
                input = "set a timer"
            )
        )
        advanceUntilIdle()

        assertEquals(0, buffer.snapshot().size)
    }

    private class FakeGatekeeperOutputSource : GatekeeperOutputSource {
        private val mutable = MutableSharedFlow<GatekeepingResult>(extraBufferCapacity = 16)
        override val results: SharedFlow<GatekeepingResult> = mutable

        fun emit(result: GatekeepingResult) {
            check(mutable.tryEmit(result)) { "tryEmit failed in test fake" }
        }
    }

    private class CannedIntentClassifier(private val record: IntentRecord?) : IntentClassifier {
        override suspend fun classify(result: GatekeepingResult): IntentRecord? = record
    }
}
