package com.google.research.air.cosmo

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GenesisRobolectricTest {
    @Test
    fun applicationContextUsesCosmoPackage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("com.google.research.air.cosmo", context.packageName)
    }
}
