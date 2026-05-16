package com.google.research.air.cosmo.lib.llm

import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AICoreDeviceSmokeTest {
    @Test
    fun aicorePackageVisibilityMatchesProductionWrapper() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val visiblePackages =
            EdgeAICoreClientWrapper.AICORE_PACKAGES.filter { packageName ->
                runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
            }
        val wrapper = EdgeAICoreClientWrapper(context, Executor { command -> command.run() })
        val available = wrapper.isAvailable()

        val args = InstrumentationRegistry.getArguments()
        Log.i(TAG, "aicore_visibility visible=$visiblePackages available=$available require=${args.requireAicore()}")
        assertEquals(
            "Production AICore wrapper availability should match package visibility",
            visiblePackages.isNotEmpty(),
            available
        )
        if (args.requireAicore()) {
            assertTrue(
                "Expected one of ${EdgeAICoreClientWrapper.AICORE_PACKAGES} to be visible to the app; visible=$visiblePackages",
                visiblePackages.isNotEmpty()
            )
            assertTrue("Production AICore wrapper should report availability", available)
        }
    }

    private companion object {
        const val TAG = "CosmoDeviceSmoke"
    }
}

private fun Bundle.requireAicore(): Boolean =
    getString("cosmo.requireAicore")?.equals("true", ignoreCase = true) == true
