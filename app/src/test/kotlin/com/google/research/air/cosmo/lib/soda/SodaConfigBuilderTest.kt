package com.google.research.air.cosmo.lib.soda

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.speech.soda.ApplicationDomainProto
import com.google.speech.soda.IntendedSpeechConfigProto
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SodaConfigBuilderTest {
    @Test
    fun coreConfigEnablesOneshotIntendedSpeech() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packDir = File(context.cacheDir, "lp_cpu").apply { mkdirs() }

        val config = SodaConfigBuilder.coreConfig(context, packDir)

        assertEquals(
            ApplicationDomainProto.ApplicationDomain.AMBIENT_ONESHOT,
            config.onDeviceAsrConfig.applicationDomain
        )
        assertTrue(config.onDeviceAsrConfig.attachHotwordEvent)
        assertTrue(config.hasIntendedSpeechConfig())
        assertTrue(config.intendedSpeechConfig.enabled)
        assertEquals(
            IntendedSpeechConfigProto.IntendedSpeechConfig.IntentDetectionMode
                .INTENT_DETECTION_MODE_OPEN_MIC,
            config.intendedSpeechConfig.intentDetectionMode
        )
        assertTrue(config.intendedSpeechConfig.attachOpenMicResult)
    }
}
