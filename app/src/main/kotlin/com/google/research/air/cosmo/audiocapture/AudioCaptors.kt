package com.google.research.air.cosmo.audiocapture

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.research.air.cosmo.context.ContextEvent
import com.google.research.air.cosmo.context.ContextStore
import com.google.research.air.cosmo.context.RetentionClass
import com.google.research.air.cosmo.context.Source
import com.google.research.air.cosmo.di.ApplicationScope
import com.google.research.air.cosmo.ephemeralbuffer.AmbientAudioBytesEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.AmbientAudioEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.BudsAudioBytesEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.BudsAudioTextEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.DeviceAudioBytesEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.DeviceAudioTextEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.WatchAudioBytesEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.WatchAudioTextEphemeralBuffer
import com.google.research.air.cosmo.lib.buds.BudsAudioProvider
import com.google.research.air.cosmo.lib.buds.SbcAudioDecoder
import com.google.research.air.cosmo.lib.record.RecordServiceListener
import com.google.research.air.cosmo.lib.record.RecordServiceManager
import com.google.research.air.cosmo.lib.soda.BudsAudioSodaController
import com.google.research.air.cosmo.lib.soda.CallAudioSodaController
import com.google.research.air.cosmo.lib.soda.DefaultMicSodaController
import com.google.research.air.cosmo.lib.soda.DeviceAudioSodaController
import com.google.research.air.cosmo.lib.soda.SodaTranscriptionController
import com.google.research.air.cosmo.lib.soda.TranscriptionData
import com.google.research.air.cosmo.lib.soda.WatchAudioSodaController
import com.google.research.air.cosmo.setting.CosmoSettingStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

interface AudioCaptor {
    fun startCapture(): Flow<TranscriptionData>

    fun flush()
}

interface AmbientAudioCaptor : AudioCaptor

interface BudsAudioCaptor : AudioCaptor

interface DeviceAudioCaptor : AudioCaptor

interface WatchAudioCaptor : AudioCaptor

abstract class SODAAudioCaptor(
    private val sourceName: String,
    protected val scope: CoroutineScope,
    protected val sodaController: SodaTranscriptionController,
    private val settings: CosmoSettingStore,
    private val contextStore: ContextStore,
    private val textBuffer: InMemoryEphemeralBuffer<Any>
) : AudioCaptor {
    protected fun startSoda() {
        sodaController.startCapture(settings.isVoiceMatchEnabled())
    }

    protected fun collectTranscriptions(send: (TranscriptionData) -> Unit) =
        scope.launch {
            sodaController.transcriptions.collect { data ->
                if (data.hasContent()) {
                    persist(data)
                    send(data)
                }
            }
        }

    override fun flush() {
        sodaController.flush()
    }

    protected fun stopSoda() {
        sodaController.stopCapture()
    }

    private suspend fun persist(data: TranscriptionData) {
        textBuffer.add(data)
        contextStore.insert(
            ContextEvent(
                id = UUID.randomUUID().toString(),
                source = Source.AUDIO,
                timestampMs = data.timestampMillis,
                text = data.transcription,
                blobRef = null,
                embedding = null,
                retentionClass = RetentionClass.EPHEMERAL,
                sourceMetadata = mapOf("captor" to sourceName)
            )
        )
    }
}

class SODAAmbientAudioCaptor
@Inject
constructor(
    @ApplicationScope scope: CoroutineScope,
    @DefaultMicSodaController sodaController: SodaTranscriptionController,
    private val recordServiceManager: RecordServiceManager,
    private val settingsStore: CosmoSettingStore,
    contextStore: ContextStore,
    @AmbientAudioEphemeralBuffer textBuffer: InMemoryEphemeralBuffer<Any>,
    @AmbientAudioBytesEphemeralBuffer private val audioBytesBuffer: InMemoryEphemeralBuffer<ByteArray>
) : SODAAudioCaptor("ambient", scope, sodaController, settingsStore, contextStore, textBuffer), AmbientAudioCaptor {
    override fun startCapture(): Flow<TranscriptionData> = callbackFlow {
        val listener =
            object : RecordServiceListener {
                override fun onBytesReceived(bytes: ByteArray) {
                    audioBytesBuffer.add(bytes)
                    sodaController.addAudioBytes(bytes)
                }

                override fun onStopRecording(error: Boolean) = Unit
            }
        startSoda()
        // C5: cosmo's SODAAmbientAudioCaptor.smali:647 reads isUseNanoappAudioEnabled and routes
        // to AOC (nanoapp on the AlwaysOn-Compute island) when true. Genesis honors the same
        // setting but falls back to mic when AOC is unavailable on the current device. The fall
        // back is silent in production logs but explicit in the AOC capability return.
        val aocStarted =
            settingsStore.isUseNanoappAudioEnabled() && recordServiceManager.startAocRecording(listener)
        if (!aocStarted) {
            recordServiceManager.startMicRecording(listener)
        }
        val collection = collectTranscriptions { trySend(it) }
        awaitClose {
            collection.cancel()
            if (aocStarted) recordServiceManager.stopAocRecording() else recordServiceManager.stopMicAudioRecording()
            recordServiceManager.unbind()
            stopSoda()
        }
    }
}

abstract class BufferBackedSODAAudioCaptor(
    sourceName: String,
    scope: CoroutineScope,
    sodaController: SodaTranscriptionController,
    settings: CosmoSettingStore,
    contextStore: ContextStore,
    textBuffer: InMemoryEphemeralBuffer<Any>,
    private val audioBytesBuffer: InMemoryEphemeralBuffer<ByteArray>,
    private val clock: Clock
) : SODAAudioCaptor(sourceName, scope, sodaController, settings, contextStore, textBuffer) {
    override fun startCapture(): Flow<TranscriptionData> = callbackFlow {
        val startedAtMillis = clock.millis()
        startSoda()
        val byteCollection =
            scope.launch {
                audioBytesBuffer.flow()
                    .filter { it.timestampMillis >= startedAtMillis }
                    .collect { entry ->
                        if (entry.value.isNotEmpty()) {
                            sodaController.addAudioBytes(entry.value)
                        }
                    }
            }
        val transcriptionCollection = collectTranscriptions { trySend(it) }
        awaitClose {
            byteCollection.cancel()
            transcriptionCollection.cancel()
            stopSoda()
        }
    }
}

class SODABudsAudioCaptor
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
    @BudsAudioSodaController sodaController: SodaTranscriptionController,
    settings: CosmoSettingStore,
    contextStore: ContextStore,
    @BudsAudioTextEphemeralBuffer textBuffer: InMemoryEphemeralBuffer<Any>,
    @BudsAudioBytesEphemeralBuffer private val audioBytesBuffer: InMemoryEphemeralBuffer<ByteArray>,
    private val budsAudioProvider: BudsAudioProvider,
    private val sbcAudioDecoder: SbcAudioDecoder,
) : SODAAudioCaptor("buds", scope, sodaController, settings, contextStore, textBuffer), BudsAudioCaptor {
    private val connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    private val bluetoothManager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    override fun startCapture(): Flow<TranscriptionData> = callbackFlow {
        val receiver = listenForConnectionChanges()
        getPixelBudsDevice()?.let(::onDeviceConnected)
        val byteCollection = startWork()
        val transcriptionCollection = collectTranscriptions { trySend(it) }
        awaitClose {
            byteCollection.cancel()
            transcriptionCollection.cancel()
            runCatching { context.unregisterReceiver(receiver) }
            stopWork()
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun getPixelBudsDevice(): BluetoothDevice? {
        if (!hasBluetoothConnectPermission()) return null
        return runCatching {
            bluetoothAdapter
                ?.bondedDevices
                ?.firstOrNull { isPixelBudsDevice(it) }
        }.getOrNull()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun isPixelBudsDevice(device: BluetoothDevice): Boolean {
        if (!hasBluetoothConnectPermission()) return false
        return runCatching {
            device.uuids?.map { it.uuid }?.contains(BudsAudioProvider.SPP_UUID) == true
        }.getOrDefault(false)
    }

    private fun listenForConnectionChanges(): BroadcastReceiver {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent
                ) {
                    val device = intent.bluetoothDeviceExtra() ?: return
                    when (intent.action) {
                        BluetoothDevice.ACTION_ACL_CONNECTED -> if (isPixelBudsDevice(device)) onDeviceConnected(device)
                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (connectedDevice.value?.address == device.address) onDeviceDisconnected()
                    }
                }
            }
        val filter =
            runCatching {
                IntentFilter().apply {
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                    addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                }
            }.getOrNull() ?: return receiver
        runCatching { ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
        return receiver
    }

    private fun onDeviceConnected(device: BluetoothDevice) {
        connectedDevice.value = device
        scope.launch { budsAudioProvider.start(device) }
    }

    private fun onDeviceDisconnected() {
        connectedDevice.value = null
        scope.launch { budsAudioProvider.stop() }
    }

    private fun startWork(): kotlinx.coroutines.Job {
        startSoda()
        val device = connectedDevice.value
        if (device != null) {
            scope.launch { budsAudioProvider.start(device) }
        }
        return scope.launch {
            budsAudioProvider.audioData.collect { chunk ->
                val decoded = sbcAudioDecoder.decode(chunk.data)
                if (decoded.isNotEmpty()) {
                    audioBytesBuffer.add(decoded)
                    sodaController.addAudioBytes(decoded)
                }
            }
        }
    }

    private fun stopWork() {
        scope.launch { budsAudioProvider.stop() }
        sbcAudioDecoder.reset()
        stopSoda()
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun Intent.bluetoothDeviceExtra(): BluetoothDevice? =
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
}

class SODADeviceAudioCaptor
@Inject
constructor(
    @ApplicationScope scope: CoroutineScope,
    @DeviceAudioSodaController sodaController: SodaTranscriptionController,
    @CallAudioSodaController private val callSodaController: SodaTranscriptionController,
    private val recordServiceManager: RecordServiceManager,
    settings: CosmoSettingStore,
    contextStore: ContextStore,
    @DeviceAudioTextEphemeralBuffer textBuffer: InMemoryEphemeralBuffer<Any>,
    @DeviceAudioBytesEphemeralBuffer private val audioBytesBuffer: InMemoryEphemeralBuffer<ByteArray>,
    private val clock: Clock
) : SODAAudioCaptor("device", scope, sodaController, settings, contextStore, textBuffer), DeviceAudioCaptor {
    override fun startCapture(): Flow<TranscriptionData> = callbackFlow {
        val startedAtMillis = clock.millis()
        val deviceListener =
            object : RecordServiceListener {
                override fun onBytesReceived(bytes: ByteArray) {
                    // The buffer fanout below is the single path into SODA, which
                    // keeps service bytes and externally injected bytes ordered.
                    audioBytesBuffer.add(bytes)
                }

                override fun onStopRecording(error: Boolean) = Unit
            }
        val callListener =
            object : RecordServiceListener {
                override fun onBytesReceived(bytes: ByteArray) {
                    callSodaController.addAudioBytes(bytes)
                }

                override fun onStopRecording(error: Boolean) = Unit
            }
        startSoda()
        callSodaController.startCapture()
        recordServiceManager.startDeviceAudioRecording(deviceListener)
        recordServiceManager.startCallAudioRecording(callListener)
        val byteCollection =
            scope.launch {
                audioBytesBuffer.flow()
                    .filter { it.timestampMillis >= startedAtMillis }
                    .collect { entry ->
                        if (entry.value.isNotEmpty()) {
                            sodaController.addAudioBytes(entry.value)
                        }
                    }
            }
        val transcriptionCollection = collectTranscriptions { trySend(it) }
        awaitClose {
            byteCollection.cancel()
            transcriptionCollection.cancel()
            recordServiceManager.stopDeviceAudioRecording()
            recordServiceManager.stopCallAudioRecording()
            recordServiceManager.unbind()
            callSodaController.stopCapture()
            stopSoda()
        }
    }
}

class SODAWatchAudioCaptor
@Inject
constructor(
    @ApplicationScope scope: CoroutineScope,
    @WatchAudioSodaController sodaController: SodaTranscriptionController,
    settings: CosmoSettingStore,
    contextStore: ContextStore,
    @WatchAudioTextEphemeralBuffer textBuffer: InMemoryEphemeralBuffer<Any>,
    @WatchAudioBytesEphemeralBuffer audioBytesBuffer: InMemoryEphemeralBuffer<ByteArray>,
    clock: Clock
) : BufferBackedSODAAudioCaptor("watch", scope, sodaController, settings, contextStore, textBuffer, audioBytesBuffer, clock), WatchAudioCaptor

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioCaptureModule {
    @Binds abstract fun bindAmbientCaptor(impl: SODAAmbientAudioCaptor): AmbientAudioCaptor

    @Binds abstract fun bindBudsCaptor(impl: SODABudsAudioCaptor): BudsAudioCaptor

    @Binds abstract fun bindDeviceCaptor(impl: SODADeviceAudioCaptor): DeviceAudioCaptor

    @Binds abstract fun bindWatchCaptor(impl: SODAWatchAudioCaptor): WatchAudioCaptor
}
