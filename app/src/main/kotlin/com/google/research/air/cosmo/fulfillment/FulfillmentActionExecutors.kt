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

class IntentFulfillmentActionExecutor
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val telemetry: CosmosTelemetry
) : FulfillmentActionExecutor {
    override fun supports(action: FulfillmentAction): Boolean =
        action is FulfillmentAction.ActiveDataPlan ||
            action is FulfillmentAction.AddTask ||
            action is FulfillmentAction.AddTimer ||
            action is FulfillmentAction.AddToList ||
            action is FulfillmentAction.CallAgent ||
            action is FulfillmentAction.CreateCalendarEvent ||
            action is FulfillmentAction.CreateDoc ||
            action is FulfillmentAction.DeepResearch ||
            action is FulfillmentAction.DefineTerm ||
            action is FulfillmentAction.DraftEmail ||
            action is FulfillmentAction.DraftSms ||
            action is FulfillmentAction.Navigation ||
            action is FulfillmentAction.PlayMusic ||
            action is FulfillmentAction.QueryPhotos ||
            action is FulfillmentAction.WebSearch

    @Suppress("CyclomaticComplexMethod")
    override suspend fun execute(action: FulfillmentAction): Boolean =
        when (action) {
            is FulfillmentAction.AddTimer ->
                context.launchFulfillmentIntent(
                    Intent(AlarmClock.ACTION_SET_TIMER)
                        .putExtra(AlarmClock.EXTRA_LENGTH, action.durationSeconds)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, action.title)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                )
            is FulfillmentAction.CreateCalendarEvent -> insertCalendarEvent(action)
            is FulfillmentAction.WebSearch ->
                context.launchFulfillmentIntent(
                    Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=${Uri.encode(action.query)}".toUri())
                )
            is FulfillmentAction.Navigation ->
                context.launchFulfillmentIntent(
                    Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(action.destination)}".toUri())
                )
            is FulfillmentAction.DraftSms ->
                context.launchFulfillmentIntent(
                    Intent(Intent.ACTION_SENDTO, "smsto:${Uri.encode(action.to)}".toUri()).putExtra("sms_body", action.body)
                )
            is FulfillmentAction.DraftEmail ->
                context.launchFulfillmentIntent(
                    Intent(Intent.ACTION_SENDTO, "mailto:${Uri.encode(action.to)}".toUri())
                        .putExtra(Intent.EXTRA_SUBJECT, action.subject)
                        .putExtra(Intent.EXTRA_TEXT, action.body)
                )
            is FulfillmentAction.PlayMusic ->
                context.launchFulfillmentIntent(
                    Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                        .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio")
                        .putExtra(SearchManager.QUERY, action.query)
                )
            is FulfillmentAction.CallAgent ->
                context.launchFulfillmentIntent(
                    Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(action.phoneNumber.orEmpty())}".toUri())
                )
            is FulfillmentAction.QueryPhotos ->
                context.launchFulfillmentIntent(
                    context.packageManager.getLaunchIntentForPackage("com.google.android.apps.photos")
                        ?: Intent(Intent.ACTION_VIEW, "content://media/external/images/media".toUri())
                )
            is FulfillmentAction.CreateDoc ->
                context.launchFulfillmentIntent(
                    Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TITLE, action.title)
                        .putExtra(Intent.EXTRA_TEXT, action.body)
                )
            is FulfillmentAction.DeepResearch ->
                context.launchFulfillmentIntent(
                    Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=${Uri.encode(action.query)}".toUri())
                )
            is FulfillmentAction.DefineTerm ->
                context.launchFulfillmentIntent(
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
            is FulfillmentAction.ActiveDataPlan ->
                context.launchFulfillmentIntent(Intent(Settings.ACTION_DATA_USAGE_SETTINGS)) ||
                    context.launchFulfillmentIntent(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            else -> false
        }

    private fun insertCalendarEvent(action: FulfillmentAction.CreateCalendarEvent): Boolean {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return context.launchFulfillmentIntent(action.calendarInsertIntent())
        }
        val calendarId = resolveWritableCalendarId()
        if (calendarId == null) {
            telemetry.log(TelemetryEvent.CalendarWritableFallback())
            return context.launchFulfillmentIntent(action.calendarInsertIntent())
        }
        val values =
            ContentValues().apply {
                put(CalendarContract.Events.TITLE, action.title)
                put(CalendarContract.Events.DTSTART, action.startMillis)
                put(CalendarContract.Events.DTEND, action.endMillis)
                put(CalendarContract.Events.EVENT_LOCATION, action.location)
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            }
        return context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) != null
    }

    private fun FulfillmentAction.CreateCalendarEvent.calendarInsertIntent(): Intent =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, location)

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
        context.launchFulfillmentIntent(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text)
        )
}

class MemoryFulfillmentActionExecutor
@Inject
constructor(
    private val memoryStorage: MemoryStorage
) : FulfillmentActionExecutor {
    override fun supports(action: FulfillmentAction): Boolean = action is FulfillmentAction.AddToMemory

    override suspend fun execute(action: FulfillmentAction): Boolean {
        if (action !is FulfillmentAction.AddToMemory) return false
        memoryStorage.add(MemoryEntry(action.text, listOf("fulfillment", action.id)))
        return true
    }
}

class TaskMonitorFulfillmentActionExecutor
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val taskMonitorNavigation: TaskMonitorNavigationBridge
) : FulfillmentActionExecutor {
    override fun supports(action: FulfillmentAction): Boolean =
        action is FulfillmentAction.BrowsingAgent ||
            action is FulfillmentAction.InvokeAgentQ ||
            action is FulfillmentAction.ProvideEventContext ||
            action is FulfillmentAction.ProvideInsight ||
            action is FulfillmentAction.ProvidePersonContext ||
            action is FulfillmentAction.ProvideRecall

    override suspend fun execute(action: FulfillmentAction): Boolean =
        when (action) {
            is FulfillmentAction.ProvideEventContext -> openTaskMonitor(action.title, action.answer, action.answer)
            is FulfillmentAction.ProvideInsight -> openTaskMonitor(action.title, action.answer, action.answer)
            is FulfillmentAction.ProvidePersonContext -> openTaskMonitor(action.title, action.answer, action.answer)
            is FulfillmentAction.ProvideRecall -> openTaskMonitor(action.title, action.answer, action.answer)
            is FulfillmentAction.BrowsingAgent -> taskMonitorNavigation.openTaskMonitor(action.title, action.prompt, "")
            is FulfillmentAction.InvokeAgentQ -> {
                val intent =
                    Intent("com.google.research.air.experiences.glimmer.altair.AGENT_Q")
                        .setPackage("com.google.research.air.experiences.glimmer.altair")
                        .putExtra("prompt", action.prompt)
                context.launchFulfillmentIntent(intent) || taskMonitorNavigation.openTaskMonitor(action.title, action.prompt, "")
            }
            else -> false
        }

    private fun openTaskMonitor(
        title: String,
        prompt: String,
        agentResponse: String
    ): Boolean = taskMonitorNavigation.openTaskMonitor(title, prompt, agentResponse)
}

class PassiveFulfillmentActionExecutor
@Inject
constructor() : FulfillmentActionExecutor {
    override fun supports(action: FulfillmentAction): Boolean =
        action is FulfillmentAction.RemovedAction || action is FulfillmentAction.Unsupported

    override suspend fun execute(action: FulfillmentAction): Boolean = false
}

private fun Context.launchFulfillmentIntent(intent: Intent): Boolean =
    try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
