package com.google.research.air.cosmo.lib.llm

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.Executor
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AICoreDeviceSmokeTest {
    @Test
    fun aicorePackageIsVisibleToProductionWrapper() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val visiblePackages =
            EdgeAICoreClientWrapper.AICORE_PACKAGES.filter { packageName ->
                runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
            }
        val wrapper = EdgeAICoreClientWrapper(context, Executor { command -> command.run() })

        assertTrue(
            "Expected one of ${EdgeAICoreClientWrapper.AICORE_PACKAGES} to be visible to the app; visible=$visiblePackages",
            visiblePackages.isNotEmpty()
        )
        assertTrue("Production AICore wrapper should report availability", wrapper.isAvailable())
    }
}
