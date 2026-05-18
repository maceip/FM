package com.google.research.air.cosmo.lib.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidTextToSpeechVoiceOutputSmokeTest {
    @Test(timeout = 30_000)
    fun platformTextToSpeechAcceptsCosmoUtterance() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val voiceOutput = AndroidTextToSpeechVoiceOutput(context)
        try {
            val result =
                runBlocking {
                    voiceOutput.speak("Cosmo voice output smoke test.")
                }
            assertTrue(
                "Platform TTS should accept a smoke utterance, got $result",
                result is VoiceOutputResult.Spoken
            )
        } finally {
            voiceOutput.shutdown()
        }
    }
}
