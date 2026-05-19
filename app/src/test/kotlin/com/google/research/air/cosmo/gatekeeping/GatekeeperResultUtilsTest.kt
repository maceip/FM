package com.google.research.air.cosmo.gatekeeping

import com.google.research.air.cosmo.fulfillment.SkillEnablement
import com.google.research.air.cosmo.gatekeeping.util.GatekeeperResultUtils
import com.google.research.air.cosmo.setting.CosmoConfiguration
import com.google.research.air.cosmo.setting.CosmoSettingStore
import com.google.research.air.cosmo.setting.proto.CosmoSetting
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatekeeperResultUtilsTest {
  private val settings = mockk<CosmoSettingStore>(relaxed = true)
  private val configuration = CosmoConfiguration()
  private val skillEnablement = SkillEnablement(settings, configuration)
  private val utils = GatekeeperResultUtils(settings, skillEnablement)

  @Test
  fun `getIntents respects suggest timer setting`() {
    every { settings.currentSetting } returns
      CosmoSetting.newBuilder()
        .setSuggestTimerEnabled(false)
        .setGlobalCosmoEnabled(true)
        .build()
    val result =
      GatekeepingResult(
        detectedSkills = mapOf("add_timer" to 0.9f),
        modelId = "test",
        detectedBy = "audio_gatekeeper",
        triggeredFulfillment = true,
        input = "set a timer",
      )
    assertTrue(utils.getIntents(result).isEmpty())
  }

  @Test
  fun `getIntents emits start timer when enabled`() {
    every { settings.currentSetting } returns
      CosmoSetting.newBuilder()
        .setSuggestTimerEnabled(true)
        .build()
    val result =
      GatekeepingResult(
        detectedSkills = mapOf("add_timer" to 0.9f),
        modelId = "test",
        detectedBy = "audio_gatekeeper",
        triggeredFulfillment = true,
        input = "set a timer",
      )
    assertTrue(utils.getIntents(result).contains("START_TIMER_INTENT"))
  }

  @Test
  fun `intent names map back to skill ids`() {
    val skills =
      GatekeeperResultUtils.intentNamesToSkillIds(
        listOf("TERM_SEARCH_QUERY", "START_TIMER_INTENT")
      )
    assertTrue(skills.contains("web_search"))
    assertTrue(skills.contains("add_timer"))
    assertFalse(skills.contains("browsing_agent"))
  }
}
