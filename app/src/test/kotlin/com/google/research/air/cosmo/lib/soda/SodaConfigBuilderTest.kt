package com.google.research.air.cosmo.lib.soda

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.speech.soda.ApplicationDomainProto
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SodaConfigBuilderTest {
    @Test
    fun coreConfigMatchesRecoveredCosmoTranscriptMode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packDir = File(context.cacheDir, "lp_cpu").apply { mkdirs() }

        val config = SodaConfigBuilder.coreConfig(context, packDir)

        assertEquals(
            ApplicationDomainProto.ApplicationDomain.AMBIENT_CONTINUOUS,
            config.onDeviceAsrConfig.applicationDomain
        )
        assertFalse(config.hasIntendedSpeechConfig())
    }
}
