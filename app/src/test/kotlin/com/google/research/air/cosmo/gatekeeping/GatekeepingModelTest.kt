package com.google.research.air.cosmo.gatekeeping

import java.nio.FloatBuffer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GatekeepingModelTest {
    @Test
    fun regexBackboneRecognizesRecoveredPhaseTwoLabels() = runTest {
        val model = RegexGatekeepingModelV0()
        val samples =
            mapOf(
                "add_to_memory" to "Ambient audio: key insight from the meeting",
                "add_to_list" to "Screen: I should get oat milk",
                "add_timer" to "Ambient audio: set a timer for ten minutes",
                "browsing_agent" to "Screen: pay this bill online",
                "call_agent" to "Device audio: call the dentist office",
                "create_calendar_event" to "Notification: schedule a meeting tomorrow",
                "create_doc" to "Screen: create a doc from these notes",
                "deep_research" to "Screen: research the market trends",
                "define_term" to "Screen: define polymorphism",
                "provide_event_context" to "Screen: the meeting about q3 roadmap",
                "provide_insight" to "Screen: pros and cons of this approach",
                "provide_person_context" to "Notification: she said the deadline moved",
                "provide_recall" to "Ambient audio: can you repeat what they just said",
                "query_photos" to "Screen: find the photos of Berlin",
                "navigation" to "Screen: navigate to 1600 Amphitheatre Parkway",
                "draft_email" to "Screen: send an email to the client",
                "add_task" to "Screen: remind me to review the spec",
                "draft_sms" to "Screen: text the doctor",
                "play_music" to "Screen: play music"
            )

        samples.forEach { (skill, input) ->
            val result = model.predict(input)
            assertTrue("$skill should be detected for $input", result.detectedSkills.containsKey(skill))
        }
    }

    @Test
    fun combinedGatekeeperFusesRegexAndBertScores() = runTest {
        val model =
            CombinedBertRegexGatekeepingModel(
                regex = RegexGatekeepingModelV0(),
                bert = bertModel(FloatArray(19).also { it[2] = 0.92f })
            )

        val result = model.predict("Screen: define polymorphism")

        assertEquals(GatekeepingDetector.FUSED, result.detector)
        assertTrue(result.triggeredFulfillment)
        assertTrue(result.detectedSkills.containsKey("define_term"))
        assertTrue(result.detectedSkills.containsKey("web_search"))
    }

    @Test
    fun bertOutputMappingUsesRecoveredNineteenFloatContract() = runTest {
        val output =
            FloatArray(19).also {
                it[0] = 0.51f
                it[2] = 0.88f
                it[10] = 0.9f
                it[12] = 0.15f
                it[18] = 0.99f
            }

        val result = bertModel(output).predict("Screen: define this and open a browsing task")

        assertEquals(GatekeepingDetector.BERT, result.detector)
        assertTrue(result.detectedSkills.containsKey("add_to_memory"))
        assertTrue(result.detectedSkills.containsKey("define_term"))
        assertTrue(result.detectedSkills.containsKey("web_search"))
        assertTrue(result.detectedSkills.containsKey("browsing_agent"))
        assertTrue(!result.detectedSkills.containsKey("add_timer"))
        assertTrue(result.screenSignals.containsKey("is_shopping_category"))
    }

    @Test
    fun bertUnavailableFailsClosed() = runTest {
        val result = bertModel(output = null, assets = emptyMap()).predict("Screen: define polymorphism")

        assertEquals(GatekeepingDetector.NONE, result.detector)
        assertTrue(result.detectedSkills.isEmpty())
        assertTrue(result.modelId.startsWith("bert_unavailable:"))
    }

    @Test
    fun nativeBertFallsBackFromAcceleratorToCpu() = runTest {
        val loader =
            fakeLoader(
                mapOf(
                    GatekeeperModelIds.BERT_VOCAB to byteArrayOf(1),
                    GatekeeperModelIds.BERT_EDGETPU_P25 to byteArrayOf(2),
                    GatekeeperModelIds.BERT_CPU to byteArrayOf(3)
                )
            )
        val createResults = ArrayDeque(listOf(0L, 7L))
        val attempts = mutableListOf<NativeCreateAttempt>()
        val model =
            BertClassifierGatekeepingModel(
                loadAssetBytes = loader,
                socProvider = { SocClass.GOOGLE_TENSOR },
                nativeCreate = { _, modelBytes, tpu ->
                    attempts += NativeCreateAttempt(modelBytes, tpu)
                    createResults.removeFirst()
                },
                nativeRun = { _, _, _ -> FloatArray(19) { index -> if (index == 2) 0.75f else 0f } },
                nativeDestroy = {}
            )

        val result = model.predict("Screen: define polymorphism")

        assertEquals(GatekeepingDetector.BERT, result.detector)
        assertEquals(
            listOf(
                NativeCreateAttempt(model = byteArrayOf(2), tpu = true),
                NativeCreateAttempt(model = byteArrayOf(3), tpu = false)
            ),
            attempts
        )
    }

    @Test
    fun nativeBertSkipsEdgeTpuCandidatesOnMediaTekSoc() = runTest {
        val loader =
            fakeLoader(
                mapOf(
                    GatekeeperModelIds.BERT_VOCAB to byteArrayOf(1),
                    GatekeeperModelIds.BERT_EDGETPU_P25 to byteArrayOf(2),
                    GatekeeperModelIds.BERT_EDGETPU_P24 to byteArrayOf(3),
                    GatekeeperModelIds.BERT_NPU_MT6989 to byteArrayOf(4),
                    GatekeeperModelIds.BERT_CPU to byteArrayOf(5)
                )
            )
        val attempts = mutableListOf<NativeCreateAttempt>()
        val model =
            BertClassifierGatekeepingModel(
                loadAssetBytes = loader,
                socProvider = { SocClass.MEDIATEK },
                nativeCreate = { _, modelBytes, tpu ->
                    attempts += NativeCreateAttempt(modelBytes, tpu)
                    9L
                },
                nativeRun = { _, _, _ -> FloatArray(19) { index -> if (index == 2) 0.75f else 0f } },
                nativeDestroy = {}
            )

        val result = model.predict("Screen: define polymorphism")

        assertEquals(GatekeepingDetector.BERT, result.detector)
        assertEquals(listOf(NativeCreateAttempt(model = byteArrayOf(4), tpu = true)), attempts)
    }

    @Test
    fun nativeBertOnOtherSocOnlyTriesCpuCandidate() = runTest {
        val loader =
            fakeLoader(
                mapOf(
                    GatekeeperModelIds.BERT_VOCAB to byteArrayOf(1),
                    GatekeeperModelIds.BERT_EDGETPU_P25 to byteArrayOf(2),
                    GatekeeperModelIds.BERT_NPU_MT6989 to byteArrayOf(4),
                    GatekeeperModelIds.BERT_CPU to byteArrayOf(5)
                )
            )
        val attempts = mutableListOf<NativeCreateAttempt>()
        val model =
            BertClassifierGatekeepingModel(
                loadAssetBytes = loader,
                socProvider = { SocClass.OTHER },
                nativeCreate = { _, modelBytes, tpu ->
                    attempts += NativeCreateAttempt(modelBytes, tpu)
                    11L
                },
                nativeRun = { _, _, _ -> FloatArray(19) { index -> if (index == 2) 0.75f else 0f } },
                nativeDestroy = {}
            )

        val result = model.predict("Screen: define polymorphism")

        assertEquals(GatekeepingDetector.BERT, result.detector)
        assertEquals(listOf(NativeCreateAttempt(model = byteArrayOf(5), tpu = false)), attempts)
    }

    @Test
    fun rawAudioGatekeeperFailsClosedWhenFrontendAssetIsMissing() = runTest {
        val model = audioModel(fakeLoader(emptyMap()))

        val result = model.predict(byteArrayOf(1, 0, 2, 0))

        // After C4-row-5 deletion, the runtime no longer reads `model-manifest.json`; missing
        // assets surface as `audio_frontend_unavailable` from ConformerEncoderImpl.
        assertTrue(result.modelId.startsWith("raw_audio_unavailable:audio_frontend_unavailable"))
        assertEquals(GatekeepingDetector.NONE, result.detector)
    }

    @Test
    fun rawAudioGatekeeperFailsClosedWhenInterpreterRuntimeUnavailable() = runTest {
        val model =
            audioModel(
                loader =
                    fakeLoader(
                        mapOf(
                            GatekeeperModelIds.AUDIO_FRONTEND to byteArrayOf(0xfe.toByte()),
                            GatekeeperModelIds.AUDIO_GATEKEEPER to byteArrayOf(0xbe.toByte())
                        )
                    ),
                nativeCreate = { _, _ -> 0L }
            )

        val result = model.predict(byteArrayOf(1, 0, 2, 0))

        assertEquals("raw_audio_unavailable:audio_litert_cpu_create_failed", result.modelId)
        assertEquals(GatekeepingDetector.NONE, result.detector)
    }

    @Test
    fun rawAudioGatekeeperRunsFrontendAndBackendInterpreters() = runTest {
        val createRequests = mutableListOf<Boolean>()
        val model =
            audioModel(
                loader =
                    fakeLoader(
                        mapOf(
                            GatekeeperModelIds.AUDIO_FRONTEND to byteArrayOf(0xfe.toByte()),
                            GatekeeperModelIds.AUDIO_GATEKEEPER to byteArrayOf(0xbe.toByte())
                        )
                    ),
                nativeCreate = { _, useNpu ->
                    createRequests += useNpu
                    if (useNpu) 11L else 10L
                },
                nativeRun = { handle, input, output ->
                    if (handle == 10L) {
                        assertEquals(ConformerEncoderImpl.MAX_SAMPLES, input.capacity())
                        assertEquals(ConformerEncoderImpl.MAX_FRAMES * ConformerEncoderImpl.MEL_BINS, output.capacity())
                        output.put(0, 0.25f)
                    } else {
                        assertEquals(ConformerEncoderImpl.MAX_FRAMES * ConformerEncoderImpl.MEL_BINS, input.capacity())
                        assertEquals(ConformerEncoderImpl.NUM_INTENTS, output.capacity())
                        output.put(0, 0.91f)
                    }
                    true
                }
            )

        val result = model.predict(byteArrayOf(0, 0, 0xff.toByte(), 0x7f))

        assertEquals(listOf(false, true), createRequests)
        assertEquals(mapOf("add_to_memory" to 0.91f), result.detectedSkills)
    }

    @Test
    fun inherentAudioGatekeeperLoadsV0AssetAndMetadataThresholds() = runTest {
        val createRequests = mutableListOf<Boolean>()
        val model =
            InherentAudioGatekeepingModel(
                loadAssetBytes =
                    fakeLoader(
                        mapOf(
                            GatekeeperModelIds.AUDIO_FRONTEND to byteArrayOf(6),
                            GatekeeperModelIds.INHERENT_V0 to byteArrayOf(7)
                        )
                    ),
                loadMetadataText = {
                    """
                    {
                      "threshold_keys_in_order": [
                        "is_interesting",
                        "has_add_to_list_intent",
                        "has_term_search_query",
                        "has_photo_query",
                        "has_calendar_event",
                        "has_create_doc_intent",
                        "has_person_context",
                        "has_event_context",
                        "has_deep_research_intent",
                        "has_insight_intent",
                        "has_browsing_agent_intent",
                        "has_calling_agent_intent",
                        "has_start_timer_intent"
                      ],
                      "default_thresholds": {
                        "is_interesting": 0.1,
                        "has_add_to_list_intent": 0.1,
                        "has_term_search_query": 0.1,
                        "has_photo_query": 0.1,
                        "has_calendar_event": 0.1,
                        "has_create_doc_intent": 0.1,
                        "has_person_context": 0.1,
                        "has_event_context": 0.1,
                        "has_deep_research_intent": 0.1,
                        "has_insight_intent": 0.1,
                        "has_browsing_agent_intent": 0.1,
                        "has_calling_agent_intent": 0.1,
                        "has_start_timer_intent": 0.1
                      }
                    }
                    """.trimIndent()
                },
                nativeCreate = { _, useNpu ->
                    createRequests += useNpu
                    if (createRequests.size == 1) 41L else 42L
                },
                nativeRun = { handle, input, output ->
                    if (handle == 41L) {
                        assertEquals(InherentAudioGatekeepingModel.MAX_SAMPLES, input.capacity())
                        assertEquals(
                            InherentAudioGatekeepingModel.MAX_FRAMES * InherentAudioGatekeepingModel.MEL_BINS,
                            output.capacity()
                        )
                        output.put(0, 0.5f)
                    } else {
                        assertEquals(42L, handle)
                        assertEquals(
                            InherentAudioGatekeepingModel.MAX_FRAMES * InherentAudioGatekeepingModel.MEL_BINS,
                            input.capacity()
                        )
                        assertEquals(InherentAudioGatekeepingModel.NUM_INTENTS, output.capacity())
                        output.put(2, 0.12f)
                        output.put(12, 0.09f)
                    }
                    true
                },
                nativeDestroy = {}
            )

        val result = model.predict(byteArrayOf(0, 0))

        assertEquals(listOf(false, false), createRequests)
        assertEquals(mapOf("define_term" to 0.12f), result.detectedSkills)
    }

    private fun bertModel(
        output: FloatArray?,
        assets: Map<String, ByteArray> =
            mapOf(
                GatekeeperModelIds.BERT_VOCAB to byteArrayOf(1),
                GatekeeperModelIds.BERT_CPU to byteArrayOf(2)
            )
    ): BertClassifierGatekeepingModel =
        BertClassifierGatekeepingModel(
            loadAssetBytes = fakeLoader(assets),
            socProvider = { SocClass.OTHER },
            nativeCreate = { _, _, _ -> if (output == null) 0L else 1L },
            nativeRun = { _, _, _ -> output },
            nativeDestroy = {}
        )

    private fun audioModel(
        loader: (String) -> ByteArray?,
        nativeCreate: (ByteArray, Boolean) -> Long = { _, useNpu -> if (useNpu) 11L else 10L },
        nativeRun: (Long, FloatBuffer, FloatBuffer) -> Boolean = { _, _, output ->
            output.put(0, 0.91f)
            true
        }
    ): AudioGatekeepingModel =
        AudioGatekeepingModel(
            ConformerEncoderImpl(
                loadAssetBytes = loader,
                nativeCreate = nativeCreate,
                nativeRun = nativeRun,
                nativeDestroy = {}
            )
        )

    private fun fakeLoader(assets: Map<String, ByteArray>): (String) -> ByteArray? = { id -> assets[id] }

    private data class NativeCreateAttempt(
        val model: ByteArray,
        val tpu: Boolean
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is NativeCreateAttempt) return false
            return model.contentEquals(other.model) && tpu == other.tpu
        }

        override fun hashCode(): Int = 31 * model.contentHashCode() + tpu.hashCode()
    }
}
