package com.google.research.air.cosmo

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.research.air.cosmo.ui.mainscreen.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class GenesisComposeSmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesMainActivity() {
        hiltRule.inject()
        composeRule.waitForIdle()
    }
}
