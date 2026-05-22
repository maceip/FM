package com.google.research.air.cosmo.lib.llm

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the default [LLM] binding on non-debuggable variants is not [SimulationLLM].
 *
 * Run on `releaseInternal` APK via [scripts/run-device-release-internal-llm-smoke.sh].
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ReleaseInternalLlmDeviceSmokeTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var llm: LLM

    @Inject lateinit var routeState: LLMRouteState

    @Test(timeout = 300_000)
    fun defaultLlmBindingIsProductionRouteAndGeneratesText() =
        runBlocking {
            val args = InstrumentationRegistry.getArguments()
            assumeTrue(
                "Set -e cosmo.releaseInternalLlmSmoke true to run release-internal LLM smoke.",
                args.enabled(RELEASE_LLM_ARG),
            )
            hiltRule.inject()

            val targetDebug = targetBuildConfigBoolean("DEBUG")
            val targetAutomation = targetBuildConfigBoolean("DEBUG_TIMER_FLOW_AUTOMATION")
            assertFalse(
                "releaseInternal must not bind SimulationLLM as the default LLM " +
                    "(target DEBUG=$targetDebug, automation=$targetAutomation)",
                llm is SimulationLLM,
            )

            val tokens = StringBuilder()
            val result =
                try {
                    llm.inference(SMOKE_PROMPT) { token -> tokens.append(token) }
                } catch (e: LlmUnavailableException) {
                    val decision = routeState.routeDecision.value
                    throw AssertionError(
                        "Default LLM unavailable on ${Build.MANUFACTURER} ${Build.MODEL}; " +
                            "path=${decision.path} reason=${decision.reason} failure=${e.reason}: ${e.message}",
                        e,
                    )
                }
            val decision = routeState.routeDecision.value
            Log.i(
                TAG,
                "release_internal_llm_smoke " +
                    "targetDebug=$targetDebug targetAutomation=$targetAutomation " +
                    "manufacturer=${Build.MANUFACTURER} model=${Build.MODEL} " +
                    "path=${decision.path} reason=${decision.reason} " +
                    "response_chars=${result.length}",
            )
            assertTrue("Default LLM route should return non-empty text", result.trim().isNotEmpty())
        }

    private companion object {
        const val TAG = "ReleaseInternalLlm"
        const val RELEASE_LLM_ARG = "cosmo.releaseInternalLlmSmoke"
        const val SMOKE_PROMPT = "Return exactly the text ok."
    }
}

private fun targetBuildConfigBoolean(field: String): Boolean {
    val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    val clazz = Class.forName("$packageName.BuildConfig")
    return clazz.getField(field).getBoolean(null)
}

private fun android.os.Bundle.enabled(name: String): Boolean =
    getString(name)?.equals("true", ignoreCase = true) == true
