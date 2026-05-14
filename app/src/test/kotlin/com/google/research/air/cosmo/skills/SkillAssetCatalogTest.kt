package com.google.research.air.cosmo.skills

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SkillAssetCatalogTest {
    @Test
    fun `catalog creates descriptors for every schema action type`() {
        val descriptors = SkillAssetCatalog(ApplicationProvider.getApplicationContext()).loadDescriptors()

        assertEquals(22, descriptors.size)
        assertNotNull(descriptors.firstOrNull { it.id == "invoke_agent_q" })
        assertNotNull(descriptors.firstOrNull { it.id == "browsing_agent" })
        assertTrue(descriptors.all { it.inputSchema.required.isNotEmpty() || it.id == "call_agent" })
    }
}
