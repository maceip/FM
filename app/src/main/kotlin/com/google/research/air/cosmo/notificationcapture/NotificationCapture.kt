package com.google.research.air.cosmo.notificationcapture

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.google.research.air.cosmo.context.ContextEvent
import com.google.research.air.cosmo.context.ContextStore
import com.google.research.air.cosmo.context.RetentionClass
import com.google.research.air.cosmo.context.Source
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.NotificationEphemeralBuffer
import dagger.Binds
import dagger.Module
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

enum class NotificationEventType { POSTED, REMOVED }

data class ActiveNotification(
    val id: Int,
    val tag: String?,
    val packageName: String,
    val title: String,
    val text: String,
    val bigText: String,
    val postTime: Long
)

data class NotificationEvent(
    val notification: ActiveNotification,
    val eventType: NotificationEventType
)

interface NotificationCaptureController {
    val permissionPromptRequest: StateFlow<Boolean>

    fun initialize()

    fun requestPermissionPrompt()

    fun dismissPermissionPrompt()

    fun getNotificationListenerSettingsIntent(): Intent

    fun isNotificationListenerAccessGranted(): Boolean
}

@Singleton
class NotificationCaptureManager
@Inject
constructor(
    @ApplicationContext private val context: Context
) : NotificationCaptureController {
    private val _permissionPromptRequest = MutableStateFlow(false)
    override val permissionPromptRequest: StateFlow<Boolean> = _permissionPromptRequest

    override fun initialize() {
        _permissionPromptRequest.value = !isNotificationListenerAccessGranted()
    }

    override fun requestPermissionPrompt() {
        _permissionPromptRequest.value = true
    }

    override fun dismissPermissionPrompt() {
        _permissionPromptRequest.value = false
    }

    override fun getNotificationListenerSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

    override fun isNotificationListenerAccessGranted(): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        return enabled.contains(context.packageName)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationCaptureModule {
    @Binds abstract fun bindNotificationCaptureController(impl: NotificationCaptureManager): NotificationCaptureController
}

@AndroidEntryPoint
class NotificationCaptureService : NotificationListenerService() {
    @Inject lateinit var contextStore: ContextStore

    @Inject @NotificationEphemeralBuffer
    lateinit var buffer: InMemoryEphemeralBuffer<Any>

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        record(sbn, NotificationEventType.POSTED)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        record(sbn, NotificationEventType.REMOVED)
    }

    private fun record(
        sbn: StatusBarNotification,
        eventType: NotificationEventType
    ) {
        val extras = sbn.notification.extras
        val active =
            ActiveNotification(
                id = sbn.id,
                tag = sbn.tag,
                packageName = sbn.packageName,
                title = extras.getCharSequence("android.title")?.toString().orEmpty(),
                text = extras.getCharSequence("android.text")?.toString().orEmpty(),
                bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty(),
                postTime = sbn.postTime
            )
        val event = NotificationEvent(active, eventType)
        buffer.add(event, sbn.postTime)
        runBlocking {
            contextStore.insert(
                ContextEvent(
                    id = UUID.randomUUID().toString(),
                    source = Source.NOTIFICATION,
                    timestampMs = sbn.postTime,
                    text = listOf(active.title, active.text, active.bigText).filter { it.isNotBlank() }.joinToString("\n"),
                    blobRef = null,
                    embedding = null,
                    retentionClass = RetentionClass.EPHEMERAL,
                    sourceMetadata = mapOf("package" to active.packageName, "event_type" to eventType.name)
                )
            )
        }
    }
}
