package com.google.research.air.cosmo.lib.soda

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SodaLanguagePackProviderSmokeTest {
    @Test
    fun selectedBundledLanguagePackExtractsToPrivateStorage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val result = SodaLanguagePackProvider(context).resolvePackDir()

        assertTrue(result is SodaPackResolution.Available)
        result as SodaPackResolution.Available
        assertTrue(result.packDir.isDirectory)
        assertTrue(result.packDir.resolve(".extracted").isFile)
        assertTrue(result.packDir.walkTopDown().any { it.isFile && it.extension == "tflite" })
    }
}
