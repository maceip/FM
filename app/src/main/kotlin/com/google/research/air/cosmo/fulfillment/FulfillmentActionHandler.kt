package com.google.research.air.cosmo.fulfillment

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.BaseColumns
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.google.research.air.cosmo.fulfillment.actions.mariner.TaskMonitorNavigationBridge
import com.google.research.air.cosmo.logging.CosmosTelemetry
import com.google.research.air.cosmo.logging.TelemetryEvent
import com.google.research.air.cosmo.memory.MemoryEntry
import com.google.research.air.cosmo.memory.MemoryStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface FulfillmentActionHandler {
    suspend fun execute(action: FulfillmentAction)
}

class FulfillmentActionHandlerImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val actionsRepository: ActionsRepositoryContract,
    private val memoryStorage: MemoryStorage,
    private val telemetry: CosmosTelemetry,
    private val taskMonitorNavigation: TaskMonitorNavigationBridge
) : FulfillmentActionHandler {
    @Suppress("CyclomaticComplexMethod")
    override suspend fun execute(action: FulfillmentAction) {
        val executed =
            when (action) {
                is FulfillmentAction.AddTimer ->
                    launchIntent(
                        Intent(
                            AlarmClock.ACTION_SET_TIMER
                        )
                            .putExtra(AlarmClock.EXTRA_LENGTH, action.durationSeconds)
                            .putExtra(AlarmClock.EXTRA_MESSAGE, action.title)
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    )
                is FulfillmentAction.CreateCalendarEvent -> insertCalendarEvent(action)
                is FulfillmentAction.WebSearch ->
                    launchIntent(
                        Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=${Uri.encode(action.query)}".toUri())
                    )
                is FulfillmentAction.Navigation ->
                    launchIntent(
                        Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(action.destination)}".toUri())
                    )
                is FulfillmentAction.DraftSms ->
                    launchIntent(
                        Intent(Intent.ACTION_SENDTO, "smsto:${Uri.encode(action.to)}".toUri()).putExtra("sms_body", action.body)
                    )
                is FulfillmentAction.DraftEmail ->
                    launchIntent(
                        Intent(
                            Intent.ACTION_SENDTO,
                            "mailto:${Uri.encode(action.to)}".toUri()
                        ).putExtra(Intent.EXTRA_SUBJECT, action.subject).putExtra(Intent.EXTRA_TEXT, action.body)
                    )
                is FulfillmentAction.PlayMusic ->
                    launchIntent(
                        Intent(
                            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH
                        ).putExtra(
                            MediaStore.EXTRA_MEDIA_FOCUS,
                            "vnd.android.cursor.item/audio"
                        ).putExtra(SearchManager.QUERY, action.query)
                    )
                is FulfillmentAction.CallAgent ->
                    launchIntent(
                        Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(action.phoneNumber.orEmpty())}".toUri())
                    )
                is FulfillmentAction.QueryPhotos ->
                    launchIntent(
                        context.packageManager.getLaunchIntentForPackage("com.google.android.apps.photos")
                            ?: Intent(Intent.ACTION_VIEW, "content://media/external/images/media".toUri())
                    )
                is FulfillmentAction.CreateDoc ->
                    launchIntent(
                        Intent(
                            Intent.ACTION_SEND
                        ).setType("text/plain").putExtra(Intent.EXTRA_TITLE, action.title).putExtra(Intent.EXTRA_TEXT, action.body)
                    )
                is FulfillmentAction.AddToMemory -> {
                    memoryStorage.add(MemoryEntry(action.text, listOf("fulfillment", action.id)))
                    true
                }
                is FulfillmentAction.DeepResearch ->
                    launchIntent(
                        Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=${Uri.encode(action.query)}".toUri())
                    )
                is FulfillmentAction.DefineTerm ->
                    launchIntent(
                        Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=${Uri.encode("define ${action.term}")}".toUri())
                    )
                is FulfillmentAction.AddTask -> launchTextDraft("Task: ${action.title}\n${action.notes}".trim())
                is FulfillmentAction.AddToList ->
                    launchTextDraft(
                        buildString {
                            appendLine(action.title)
                            action.items.forEach { appendLine("- $it") }
                        }.trim()
                    )
                is FulfillmentAction.ProvideEventContext -> openTaskMonitor(action.title, action.answer, action.answer)
                is FulfillmentAction.ProvideInsight -> openTaskMonitor(action.title, action.answer, action.answer)
                is FulfillmentAction.ProvidePersonContext -> openTaskMonitor(action.title, action.answer, action.answer)
                is FulfillmentAction.ProvideRecall -> openTaskMonitor(action.title, action.answer, action.answer)
                is FulfillmentAction.ActiveDataPlan ->
                    launchIntent(Intent(Settings.ACTION_DATA_USAGE_SETTINGS)) ||
                        launchIntent(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                is FulfillmentAction.RemovedAction -> false
                is FulfillmentAction.Unsupported -> false

                is FulfillmentAction.BrowsingAgent ->
                    taskMonitorNavigation.openTaskMonitor(action.title, action.prompt, "")
                is FulfillmentAction.InvokeAgentQ -> {
                    val intent =
                        Intent("com.google.research.air.experiences.glimmer.altair.AGENT_Q")
                            .setPackage("com.google.research.air.experiences.glimmer.altair")
                            .putExtra("prompt", action.prompt)
                    launchIntent(intent) || taskMonitorNavigation.openTaskMonitor(action.title, action.prompt, "")
                }
            }
        telemetry.log(TelemetryEvent.ActionExecution(action.id, action.actionTypeName(), executed))
        if (executed) {
            actionsRepository.markActionAsExecuted(action.id)
        } else {
            actionsRepository.markActionAsDismissed(action.id)
        }
    }

    private fun insertCalendarEvent(action: FulfillmentAction.CreateCalendarEvent): Boolean {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return launchIntent(
                Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, action.title)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, action.startMillis)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, action.endMillis)
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, action.location)
            )
        }
        val calendarId = resolveWritableCalendarId()
        if (calendarId == null) {
            telemetry.log(TelemetryEvent.CalendarWritableFallback())
            return launchIntent(
                Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, action.title)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, action.startMillis)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, action.endMillis)
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, action.location)
            )
        }
        val values =
            ContentValues().apply {
                put(CalendarContract.Events.TITLE, action.title)
                put(CalendarContract.Events.DTSTART, action.startMillis)
                put(CalendarContract.Events.DTEND, action.endMillis)
                put(CalendarContract.Events.EVENT_LOCATION, action.location)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(
                    CalendarContract.Events.EVENT_TIMEZONE,
                    java.util.TimeZone
                        .getDefault()
                        .id
                )
            }
        return context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
    }

    /**
     * Picks a visible primary-or-first calendar the user can write to instead of assuming [CalendarContract.Events.CALENDAR_ID] `1`.
     */
    private fun resolveWritableCalendarId(): Long? {
        val projection =
            arrayOf(
                BaseColumns._ID,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.IS_PRIMARY
            )
        val selection = "${CalendarContract.Calendars.VISIBLE} = ?"
        val args = arrayOf("1")
        val sort = "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${BaseColumns._ID} ASC"
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, args, sort)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(BaseColumns._ID)
            val accessIdx = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            while (cursor.moveToNext()) {
                val access = cursor.getInt(accessIdx)
                if (access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                    return cursor.getLong(idIdx)
                }
            }
        }
        return null
    }

    private fun launchTextDraft(text: String): Boolean =
        launchIntent(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
        )

    private fun openTaskMonitor(
        title: String,
        prompt: String,
        agentResponse: String
    ): Boolean = taskMonitorNavigation.openTaskMonitor(title, prompt, agentResponse)

    private fun launchIntent(intent: Intent): Boolean =
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
}
