package com.google.research.air.cosmo.lib.soda

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Clock
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SodaJniSmokeTest {
    @Test
    fun recoveredCompatibleNativeLifecycleAcceptsPcm() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pack =
            when (val result = kotlinx.coroutines.runBlocking { SodaLanguagePackProvider(context).resolvePackDir() }) {
                is SodaPackResolution.Available -> result
                is SodaPackResolution.Unavailable -> error("SODA pack unavailable: ${result.reason}")
                SodaPackResolution.Unconfigured -> error("SODA pack unconfigured")
            }
        val session = SodaSession(context, pack.packDir, Clock.systemUTC())
        assertTrue(session.start(SodaTranscriptCallback { _, _ -> }) is SodaStartResult.Started)

        val pcm = ByteBuffer.allocateDirect(PCM_BYTES).order(ByteOrder.nativeOrder())
        repeat(PCM_BYTES / Short.SIZE_BYTES) {
            pcm.putShort(0)
        }
        pcm.position(0)
        session.addAudio(pcm, PCM_BYTES)
        session.stop()
    }

    private companion object {
        const val PCM_BYTES = 16_000
    }
}
