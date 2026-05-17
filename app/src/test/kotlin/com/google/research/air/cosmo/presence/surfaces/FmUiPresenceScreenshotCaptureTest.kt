package com.google.research.air.cosmo.presence.surfaces

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import java.io.File
import java.io.FileOutputStream
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FmUiPresenceScreenshotCaptureTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun captureFrozenFmUiPreviewPngs() {
        assumeTrue("Set -Dcosmo.captureFmUiScreenshots=true to regenerate FM UI PNG artifacts.", shouldCaptureScreenshots())
        val outputDir = File("artifacts/fm_ui/generated-compose")
        outputDir.mkdirs()

        fixtures.forEach { fixture ->
            rule.setContent {
                Box(Modifier.testTag(CAPTURE_TAG)) {
                    fixture.content()
                }
            }
            rule.waitForIdle()
            val bitmap = rule.onNodeWithTag(CAPTURE_TAG).captureToImage().asAndroidBitmap()
            FileOutputStream(File(outputDir, fixture.fileName)).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
    }

    private data class ScreenshotFixture(
        val fileName: String,
        val content: @Composable () -> Unit
    )

    private companion object {
        private const val CAPTURE_TAG = "fm_ui_capture"

        private fun shouldCaptureScreenshots(): Boolean =
            System.getProperty("cosmo.captureFmUiScreenshots").toBoolean()

        val fixtures =
            listOf(
                ScreenshotFixture("orb_variant1_listening.png") { FmUiOrbListeningPreview() },
                ScreenshotFixture("orb_variant2_working.png") { FmUiOrbWorkingPreview() },
                ScreenshotFixture("orb_variant3_needs_input.png") { FmUiOrbNeedsInputPreview() },
                ScreenshotFixture("tab_minimized.png") { FmUiTabMinimizedPreview() },
                ScreenshotFixture("tab_expanded.png") { FmUiTabExpandedPreview() },
                ScreenshotFixture("stack_variant1_browser.png") { FmUiStackBrowserPreview() },
                ScreenshotFixture("stack_variant2_messaging.png") { FmUiStackMessagingPreview() },
                ScreenshotFixture("stack_variant3_calendar.png") { FmUiStackCalendarPreview() },
                ScreenshotFixture("stack_mobile_context.png") { FmUiStackMobileContextPreview() },
                ScreenshotFixture("stack_foldable_context.png") { FmUiStackFoldableContextPreview() }
            )
    }
}
