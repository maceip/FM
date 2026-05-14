package com.google.research.air.cosmo.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.research.air.cosmo.R
import com.google.research.air.cosmo.ui.mainscreen.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CosmoNotificationManager
@Inject
constructor(
    @ApplicationContext private val context: Context
) {
    fun createNotificationChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(AUDIO_RECORD_CHANNEL_ID, "Recording Audio", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Phone microphone is capturing audio."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(WEARABLE_CHANNEL_ID, "Wearable Service", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CAPTURE_STATUS_CHANNEL_ID, "Cosmo Status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ambient capture status and controls."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(TASK_PROGRESS_CHANNEL_ID, "Cosmo Tasks", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "User-started task progress."
            }
        )
    }

    fun showCaptureStatus(
        title: String,
        status: String,
        ongoing: Boolean
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(CAPTURE_STATUS_NOTIFICATION_ID, captureStatusNotification(title, status, ongoing))
    }

    fun clearCaptureStatus() {
        context.getSystemService(NotificationManager::class.java).cancel(CAPTURE_STATUS_NOTIFICATION_ID)
    }

    fun showTaskProgress(
        title: String,
        status: String,
        progress: Int,
        max: Int
    ) {
        val notification =
            NotificationCompat
                .Builder(context, TASK_PROGRESS_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(status)
                .setContentIntent(mainActivityIntent())
                .setOnlyAlertOnce(true)
                .setProgress(max, progress.coerceIn(0, max), false)
                .build()
        context.getSystemService(NotificationManager::class.java).notify(TASK_PROGRESS_NOTIFICATION_ID, notification)
    }

    private fun captureStatusNotification(
        title: String,
        status: String,
        ongoing: Boolean
    ): Notification =
        NotificationCompat
            .Builder(context, CAPTURE_STATUS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(status)
            .setContentIntent(mainActivityIntent())
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .build()

    private fun mainActivityIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            MAIN_ACTIVITY_REQUEST_CODE,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun buildAndroid16ProgressExtras(
        status: String,
        progress: Int,
        max: Int
    ): Map<String, Any> {
        // Kept separate from NotificationCompat until SDK stubs expose Notification.ProgressStyle.
        return mapOf("status" to status, "progress" to progress.coerceIn(0, max), "max" to max)
    }

    companion object {
        const val AUDIO_RECORD_CHANNEL_ID = "audio_record"
        const val WEARABLE_CHANNEL_ID = "WearableServiceChannel"
        const val CAPTURE_STATUS_CHANNEL_ID = "cosmo_capture_status"
        const val TASK_PROGRESS_CHANNEL_ID = "cosmo_task_progress"
        private const val CAPTURE_STATUS_NOTIFICATION_ID = 1402
        private const val TASK_PROGRESS_NOTIFICATION_ID = 1403
        private const val MAIN_ACTIVITY_REQUEST_CODE = 1404
    }
}
