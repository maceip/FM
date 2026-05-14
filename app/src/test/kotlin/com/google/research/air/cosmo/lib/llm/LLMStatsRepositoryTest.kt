package com.google.research.air.cosmo.lib.llm

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LLMStatsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun statsPersistToDataStoreAndReloadIntoFreshRepository() = runBlocking {
        val file = File(temporaryFolder.root, "llm_stats.preferences_pb")
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val repository = LLMStatsRepository(dataStore, scope)

        try {
            repository.incrementHit()
            repository.incrementSuccess()
            repository.incrementFailure()
            repository.updateRemoteInferenceEnabled(true)

            withTimeout(15_000) {
                dataStore.data.first { prefs ->
                    prefs[LLMStatsRepository.HIT] == 1 &&
                        prefs[LLMStatsRepository.SUCCESS] == 1 &&
                        prefs[LLMStatsRepository.FAILURE] == 1 &&
                        prefs[LLMStatsRepository.REMOTE_INFERENCE_ENABLED] == true
                }
            }

            val reloaded = LLMStatsRepository(dataStore, scope)
            reloaded.refreshFromDisk()

            assertEquals(LLMInferenceStats(1, 1, 1), reloaded.inferenceStats.value)
            assertTrue(reloaded.isRemoteInferenceEnabled.value)
        } finally {
            scope.cancel()
            dispatcher.close()
        }
    }
}
