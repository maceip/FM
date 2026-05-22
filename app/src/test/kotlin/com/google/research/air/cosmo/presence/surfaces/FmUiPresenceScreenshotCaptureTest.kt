package com.google.research.air.cosmo.presence.surfaces

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FmUiPresenceScreenshotCaptureTest {

    @Test
    fun captureFrozenFmUiPreviewPngs() {
        assumeTrue("Set -Dcosmo.captureFmUiScreenshots=true to regenerate FM UI PNG artifacts.", shouldCaptureScreenshots())
        val outputDir = File(screenshotOutputDir())
        outputDir.deleteRecursively()
        check(outputDir.mkdirs()) { "Unable to create FM UI screenshot output directory: ${outputDir.absolutePath}" }

        fixtures.forEach { fixture ->
            val bitmap = renderFixture(fixture)
            FileOutputStream(File(outputDir, fixture.fileName)).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
    }

    private data class ScreenshotFixture(
        val fileName: String,
        val widthDp: Int,
        val heightDp: Int,
        val content: @Composable () -> Unit
    )

    private fun renderFixture(fixture: ScreenshotFixture): Bitmap {
        val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = activityController.get()
        val composeView =
            ComposeView(activity).apply {
                setContent { fixture.content() }
            }

        activity.setContentView(composeView)
        shadowOf(Looper.getMainLooper()).idle()

        val density = activity.resources.displayMetrics.density
        val widthPx = (fixture.widthDp * density).roundToInt()
        val heightPx = (fixture.heightDp * density).roundToInt()
        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)

        composeView.measure(widthSpec, heightSpec)
        composeView.layout(0, 0, widthPx, heightPx)
        shadowOf(Looper.getMainLooper()).idle()

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        composeView.draw(Canvas(bitmap))
        activityController.destroy()
        return bitmap
    }

    private companion object {
        private fun shouldCaptureScreenshots(): Boolean =
            System.getProperty("cosmo.captureFmUiScreenshots").toBoolean()

        private fun screenshotOutputDir(): String =
            System.getProperty("cosmo.fmUiScreenshotDir")
                ?: "artifacts/fm_ui/generated-compose"

        val fixtures =
            listOf(
                ScreenshotFixture("orb_variant1_listening.png", 230, 658) { FmUiOrbListeningPreview() },
                ScreenshotFixture("orb_variant2_working.png", 230, 658) { FmUiOrbWorkingPreview() },
                ScreenshotFixture("orb_variant3_needs_input.png", 230, 658) { FmUiOrbNeedsInputPreview() },
                ScreenshotFixture("tab_minimized.png", 30, 300) { FmUiTabMinimizedPreview() },
                ScreenshotFixture("tab_expanded.png", 528, 280) { FmUiTabExpandedPreview() },
                ScreenshotFixture("card_browser.png", 1122, 1402) { FmUiCardBrowserPreview() },
                ScreenshotFixture("card_calling.png", 1122, 1402) { FmUiCardCallingPreview() },
                ScreenshotFixture("card_messaging.png", 1122, 1402) { FmUiCardMessagingPreview() },
                ScreenshotFixture("card_document.png", 1122, 1402) { FmUiCardDocumentPreview() },
                ScreenshotFixture("card_tasks.png", 1122, 1402) { FmUiCardTasksPreview() },
                ScreenshotFixture("card_calendar.png", 1122, 1402) { FmUiCardCalendarPreview() },
                ScreenshotFixture("card_navigation.png", 1122, 1402) { FmUiCardNavigationPreview() },
                ScreenshotFixture("card_media_playback.png", 1122, 1402) { FmUiCardMediaPlaybackPreview() },
                ScreenshotFixture("card_people.png", 1122, 1402) { FmUiCardPeoplePreview() },
                ScreenshotFixture("card_media_image.png", 1122, 1402) { FmUiCardMediaImagePreview() },
                ScreenshotFixture("card_source_citation.png", 1122, 1402) { FmUiCardSourceCitationPreview() },
                ScreenshotFixture("card_permission.png", 1122, 1402) { FmUiCardPermissionPreview() },
                ScreenshotFixture("card_settings.png", 904, 514) { FmUiCardSettingsPreview() },
                ScreenshotFixture("card_onboarding.png", 904, 572) { FmUiCardOnboardingPreview() },
                ScreenshotFixture("card_reference_grid.png", 1122, 1402) { FmUiCardReferenceGridPreview() },
                ScreenshotFixture("mock_card_deep_research.png", 360, 460) { FmUiMockDeepResearchPreview() },
                ScreenshotFixture("mock_card_agent_contact.png", 360, 460) { FmUiMockAgentContactPreview() },
                ScreenshotFixture("mock_card_message_compose.png", 360, 460) { FmUiMockMessageComposePreview() },
                ScreenshotFixture("mock_card_document_overview.png", 360, 460) { FmUiMockDocumentOverviewPreview() },
                ScreenshotFixture("mock_card_action_items.png", 360, 460) { FmUiMockActionItemsPreview() },
                ScreenshotFixture("mock_card_event_scheduler.png", 360, 460) { FmUiMockEventSchedulerPreview() },
                ScreenshotFixture("mock_card_route_assist.png", 360, 460) { FmUiMockRouteAssistPreview() },
                ScreenshotFixture("mock_card_media_queue.png", 360, 460) { FmUiMockMediaQueuePreview() },
                ScreenshotFixture("mock_card_contact_profile.png", 360, 460) { FmUiMockContactProfilePreview() },
                ScreenshotFixture("mock_card_image_explorer.png", 360, 460) { FmUiMockImageExplorerPreview() },
                ScreenshotFixture("mock_card_citation_generator.png", 360, 460) { FmUiMockCitationGeneratorPreview() },
                ScreenshotFixture("mock_card_action_permission.png", 360, 460) { FmUiMockActionPermissionPreview() },
                ScreenshotFixture("stack_variant1_browser.png", 1122, 1402) { FmUiStackBrowserPreview() },
                ScreenshotFixture("stack_variant2_messaging.png", 1122, 1402) { FmUiStackMessagingPreview() },
                ScreenshotFixture("stack_variant3_calendar.png", 1122, 1402) { FmUiStackCalendarPreview() },
                ScreenshotFixture("stack_mobile_context.png", 1122, 1402) { FmUiStackMobileContextPreview() },
                ScreenshotFixture("stack_foldable_context.png", 1448, 1086) { FmUiStackFoldableContextPreview() },
                ScreenshotFixture("stack_ten_card_overflow.png", 1122, 1402) { FmUiStackTenCardOverflowPreview() }
            )
    }
}
