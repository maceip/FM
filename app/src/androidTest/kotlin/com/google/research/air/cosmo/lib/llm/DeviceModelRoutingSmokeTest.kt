package com.google.research.air.cosmo.lib.llm

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DeviceModelRoutingSmokeTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @HybridLLM
    lateinit var hybrid: LLM

    @Inject
    lateinit var routeState: LLMRouteState

    @Test(timeout = 240_000)
    fun productionHybridRouteGeneratesNonEmptyText() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Set -e cosmo.productionLlmSmoke true to run the real-device production LLM route smoke.",
            args.enabled("cosmo.productionLlmSmoke")
        )
        hiltRule.inject()

        val tokens = StringBuilder()
        val result =
            try {
                hybrid.inference(SMOKE_PROMPT) { token -> tokens.append(token) }
            } catch (e: LlmUnavailableException) {
                val decision = routeState.routeDecision.value
                throw AssertionError(
                    "Production LLM route unavailable on ${Build.MANUFACTURER} ${Build.MODEL}; " +
                        "path=${decision.path} reason=${decision.reason} failure=${e.reason}: ${e.message}",
                    e
                )
            }
        val decision = routeState.routeDecision.value

        Log.i(
            TAG,
            "device_model_routing_smoke " +
                "manufacturer=${Build.MANUFACTURER} model=${Build.MODEL} " +
                "sdk=${Build.VERSION.SDK_INT} fingerprint=${Build.FINGERPRINT} " +
                "path=${decision.path} reason=${decision.reason} " +
                "response_chars=${result.length} token_chars=${tokens.length}"
        )
        assertTrue("Production LLM route should return non-empty text", result.trim().isNotEmpty())
    }

    private companion object {
        const val TAG = "CosmoDeviceSmoke"
        const val SMOKE_PROMPT = "Return exactly the text ok."
    }
}

private fun Bundle.enabled(name: String): Boolean =
    getString(name)?.equals("true", ignoreCase = true) == true
