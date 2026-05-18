package com.google.research.air.cosmo.lib.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SherpaVoiceOutputSmokeTest {
    @Test
    fun sherpaVoiceOutputAcceptsCosmoUtteranceWhenAssetsAreBundled() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assumeTrue(hasAsset(context.assets, MODEL_ASSET_PATH))

            val voiceOutput = SherpaVoiceOutput(context)
            try {
                val result =
                    withTimeout(SMOKE_TIMEOUT_MS) {
                        voiceOutput.speak("I can handle that.")
                    }
                assertTrue(
                    "Expected Sherpa TTS to synthesize speech, got $result",
                    result is VoiceOutputResult.Spoken
                )
            } finally {
                voiceOutput.stop()
            }
        }

    private fun hasAsset(
        assets: android.content.res.AssetManager,
        path: String
    ): Boolean =
        runCatching {
            assets.open(path).use { }
        }.isSuccess

    private companion object {
        const val MODEL_ASSET_PATH = "models/tts/kitten-mini-en-v0_1-fp16/model.fp16.onnx"
        const val SMOKE_TIMEOUT_MS = 120_000L
    }
}
