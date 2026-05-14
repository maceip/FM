package com.google.research.air.cosmo.lib.record

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.research.air.cosmo.R
import com.google.research.air.cosmo.notifications.CosmoNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface RecordServiceListener {
    fun onBytesReceived(bytes: ByteArray)

    fun onStopRecording(error: Boolean)
}

@AndroidEntryPoint
class RecordService : Service() {
    inner class LocalBinder : Binder() {
        fun service(): RecordService = this@RecordService
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var micJob: Job? = null
    private var deviceAudioJob: Job? = null
    private var callAudioJob: Job? = null

    override fun onBind(intent: Intent): IBinder = LocalBinder()

    override fun onDestroy() {
        stopMicAudioRecording()
        stopDeviceAudioRecording()
        stopCallAudioRecording()
        scope.cancel()
        super.onDestroy()
    }

    fun startMicRecording(listener: RecordServiceListener) {
        startAudioRecording(
            listener = listener,
            currentJob = micJob,
            audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION,
            notificationText = "Phone microphone is capturing audio.",
            assignJob = { micJob = it }
        )
    }

    fun startDeviceAudioRecording(listener: RecordServiceListener) {
        startAudioRecording(
            listener = listener,
            currentJob = deviceAudioJob,
            audioSource = DEVICE_AUDIO_SOURCE_REMOTE_SUBMIX,
            notificationText = "Device audio is capturing media playback.",
            assignJob = { deviceAudioJob = it }
        )
    }

    fun startCallAudioRecording(listener: RecordServiceListener) {
        startAudioRecording(
            listener = listener,
            currentJob = callAudioJob,
            audioSource = CALL_AUDIO_SOURCE_VOICE_CALL,
            notificationText = "Call audio is capturing voice communication.",
            assignJob = { callAudioJob = it }
        )
    }

    private fun startAudioRecording(
        listener: RecordServiceListener,
        currentJob: Job?,
        audioSource: Int,
        notificationText: String,
        assignJob: (Job?) -> Unit
    ) {
        if (currentJob?.isActive == true) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.onStopRecording(true)
            return
        }
        startForeground(NOTIFICATION_ID, createNotification(notificationText))
        val sampleRate = 16_000
        val channel = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minSize = AudioRecord.getMinBufferSize(sampleRate, channel, encoding).coerceAtLeast(sampleRate)
        val audioRecord =
            runCatching { AudioRecord(audioSource, sampleRate, channel, encoding, minSize * 2) }
                .getOrElse {
                    listener.onStopRecording(true)
                    return
                }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            listener.onStopRecording(true)
            return
        }
        assignJob(
            scope.launch {
                val buffer = ByteArray(minSize)
                var failed = false
                try {
                    audioRecord.startRecording()
                    while (isActive) {
                        val read = audioRecord.read(buffer, 0, buffer.size)
                        if (read > 0) listener.onBytesReceived(buffer.copyOf(read))
                    }
                } catch (_: RuntimeException) {
                    failed = true
                } finally {
                    runCatching { audioRecord.stop() }
                    audioRecord.release()
                    listener.onStopRecording(failed)
                    if (!isAnyAudioRecording()) stopForeground(STOP_FOREGROUND_DETACH)
                }
            }
        )
    }

    fun stopMicAudioRecording() {
        micJob?.cancel()
        micJob = null
    }

    fun stopDeviceAudioRecording() {
        deviceAudioJob?.cancel()
        deviceAudioJob = null
    }

    fun stopCallAudioRecording() {
        callAudioJob?.cancel()
        callAudioJob = null
    }

    fun isMicRecording(): Boolean = micJob?.isActive == true

    fun isDeviceAudioRecording(): Boolean = deviceAudioJob?.isActive == true

    fun isCallAudioRecording(): Boolean = callAudioJob?.isActive == true

    private fun isAnyAudioRecording(): Boolean =
        isMicRecording() || isDeviceAudioRecording() || isCallAudioRecording()

    private fun createNotification(text: String): Notification = NotificationCompat
        .Builder(this, CosmoNotificationManager.AUDIO_RECORD_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(text)
        .setOngoing(true)
        .build()

    companion object {
        private const val NOTIFICATION_ID = 1401
        private const val DEVICE_AUDIO_SOURCE_REMOTE_SUBMIX = 8
        private const val CALL_AUDIO_SOURCE_VOICE_CALL = 4
    }
}

@Singleton
class RecordServiceManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val nanoAppClient: NanoAppClient
) {
    private var service: RecordService? = null
    private var bound = false
    private var binding = false
    private val pendingActions = mutableListOf<(RecordService) -> Unit>()
    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?
            ) {
                service = (binder as? RecordService.LocalBinder)?.service()
                bound = true
                binding = false
                service?.let { connectedService ->
                    val actions = pendingActions.toList()
                    pendingActions.clear()
                    actions.forEach { action -> action(connectedService) }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                bound = false
                binding = false
                service = null
            }
        }

    fun bind() {
        if (bound || binding) return
        val intent = Intent(context, RecordService::class.java)
        binding = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        pendingActions.clear()
        if (bound) context.unbindService(connection)
        bound = false
        binding = false
        service = null
    }

    fun getRecordService(): RecordService? = service.also {
        if (it == null) bind()
    }

    fun startMicRecording(listener: RecordServiceListener) {
        withService { service -> service.startMicRecording(listener) }
    }

    /**
     * Always-On Compute (nanoapp) audio recording. Routes through the Pixel Tensor AOC island via
     * Context Hub when `isUseNanoappAudioEnabled` is on; the audio frames arrive on
     * `ContextHubClientCallback.onMessageFromNanoApp` rather than via `AudioRecord`. The bind is
     * managed by [NanoAppClient], which mirrors cosmo's recovered shape. Returns false when:
     *   - the platform has no `ContextHubManager`,
     *   - no Context Hub is present on the device,
     *   - the AOC ambient-audio nanoapp is not loaded on that hub, or
     *   - `sendMessageToNanoApp` returns a non-OK status.
     * Callers MUST honor the false return and fall back to mic recording.
     */
    fun startAocRecording(listener: RecordServiceListener): Boolean {
        nanoAppClient.recordListener = listener
        val started = nanoAppClient.startRecording()
        if (!started) nanoAppClient.recordListener = null
        return started
    }

    fun startDeviceAudioRecording(listener: RecordServiceListener) {
        withService { service -> service.startDeviceAudioRecording(listener) }
    }

    fun startCallAudioRecording(listener: RecordServiceListener) {
        withService { service -> service.startCallAudioRecording(listener) }
    }

    fun stopAocRecording() {
        nanoAppClient.stopRecording()
        nanoAppClient.recordListener = null
    }

    fun stopMicAudioRecording() {
        service?.stopMicAudioRecording()
    }

    fun stopDeviceAudioRecording() {
        service?.stopDeviceAudioRecording()
    }

    fun stopCallAudioRecording() {
        service?.stopCallAudioRecording()
    }

    private fun withService(action: (RecordService) -> Unit) {
        val current = service
        if (bound && current != null) {
            action(current)
            return
        }
        pendingActions += action
        bind()
    }
}
