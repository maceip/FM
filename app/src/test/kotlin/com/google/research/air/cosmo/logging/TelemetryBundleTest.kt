package com.google.research.air.cosmo.logging

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TelemetryBundleTest {
    @Test
    fun filteredTelemetryCopy_retainsOnlyAllowlistedKeys() {
        val input =
            Bundle().apply {
                putString("surface", "nav3_in_app")
                putString("extra_leak", "remove_me")
                putInt("prompt_length", 12)
            }
        val out = input.filteredTelemetryCopy(setOf("surface", "prompt_length"))
        assertEquals("nav3_in_app", out.getString("surface"))
        assertEquals(12, out.getInt("prompt_length"))
        assertFalse(out.containsKey("extra_leak"))
    }

    @Test
    fun filteredTelemetryCopy_preservesBooleanFromBundle() {
        val input =
            Bundle().apply {
                putBoolean("succeeded", true)
            }
        val out = input.filteredTelemetryCopy(setOf("succeeded"))
        assertTrue(out.getBoolean("succeeded"))
    }
}
