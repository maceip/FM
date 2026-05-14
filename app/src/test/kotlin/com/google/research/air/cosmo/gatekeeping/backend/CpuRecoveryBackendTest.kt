package com.google.research.air.cosmo.gatekeeping.backend

import com.google.research.air.cosmo.gatekeeping.GatekeepingDetector
import com.google.research.air.cosmo.gatekeeping.GatekeepingInputSource
import com.google.research.air.cosmo.gatekeeping.GatekeepingModel
import com.google.research.air.cosmo.gatekeeping.GatekeepingResult
import com.google.research.air.cosmo.gatekeeping.api.ContextSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CpuRecoveryBackendTest {
    @Test
    fun ambientSourceWithDirectednessTriggerInvokesBert() = runTest {
        val bert = RecordingTextModel()
        val backend = CpuRecoveryBackend(bert)

        val result =
            backend.classify(
                AudioInput.TranscribedSegment(
                    source = ContextSource.AMBIENT_AUDIO,
                    transcript = "Set a timer for ten minutes",
                    directednessTrigger = true
                )
            )

        assertNotNull(result)
        assertEquals("Set a timer for ten minutes", bert.lastInput)
    }

    @Test
    fun ambientSourceWithoutDirectednessTriggerReturnsNullAndSkipsBert() = runTest {
        val bert = RecordingTextModel()
        val backend = CpuRecoveryBackend(bert)

        val result =
            backend.classify(
                AudioInput.TranscribedSegment(
                    source = ContextSource.AMBIENT_AUDIO,
                    transcript = "some ambient noise",
                    directednessTrigger = false
                )
            )

        assertNull(result)
        assertNull(bert.lastInput)
    }

    @Test
    fun budsSourceBypassesDirectednessGate() = runTest {
        // Per architecture-delta G7: trusted sources (buds/device/watch) don't go through
        // the Magic Mic gate because the user explicitly attached them.
        val bert = RecordingTextModel()
        val backend = CpuRecoveryBackend(bert)

        val result =
            backend.classify(
                AudioInput.TranscribedSegment(
                    source = ContextSource.BUDS_AUDIO,
                    transcript = "buds transcript",
                    directednessTrigger = false
                )
            )

        assertNotNull(result)
        assertEquals("buds transcript", bert.lastInput)
    }

    @Test
    fun backendIdIsCpuRecovery() {
        val backend = CpuRecoveryBackend(RecordingTextModel())
        assertEquals(BackendId.CpuRecovery, backend.backendId)
        assertEquals(true, backend.isAvailable)
    }

    private class RecordingTextModel : GatekeepingModel {
        var lastInput: String? = null

        override suspend fun predict(input: String): GatekeepingResult {
            lastInput = input
            return GatekeepingResult(
                detectedSkills = mapOf("add_timer" to 0.9f),
                modelId = "bert",
                detectedBy = "bert",
                triggeredFulfillment = true,
                input = input,
                inputSource = GatekeepingInputSource.AMBIENT_AUDIO,
                detector = GatekeepingDetector.BERT
            )
        }
    }
}
