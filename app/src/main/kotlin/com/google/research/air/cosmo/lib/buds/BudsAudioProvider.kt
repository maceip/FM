package com.google.research.air.cosmo.lib.buds

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.research.air.cosmo.di.ApplicationScope
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BudsAudioChunk(
    val data: ByteArray,
    val timestampMillis: Long = System.currentTimeMillis()
)

sealed interface BudsProviderState {
    data object Idle : BudsProviderState
    data object Connecting : BudsProviderState
    data object Streaming : BudsProviderState
    data class Failed(val reason: String) : BudsProviderState
}

interface BudsAudioProvider {
    val state: StateFlow<BudsProviderState>

    val audioData: Flow<BudsAudioChunk>

    suspend fun start(device: BluetoothDevice)

    suspend fun stop()

    fun shutdown()

    companion object {
        val SPP_UUID: UUID = UUID.fromString("25e97ff7-24ce-4c4c-8951-f764a708f7b4")
    }
}

@Singleton
class BudsAudioProviderImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope
) : BudsAudioProvider {
    private val connectionMutex = Mutex()
    private var connectionJob: Job? = null
    private var budsSocket: BluetoothSocket? = null
    private val _state = MutableStateFlow<BudsProviderState>(BudsProviderState.Idle)
    private val _audioData = MutableSharedFlow<BudsAudioChunk>(extraBufferCapacity = 10)

    override val state: StateFlow<BudsProviderState> = _state.asStateFlow()
    override val audioData: Flow<BudsAudioChunk> = _audioData.asSharedFlow()

    override suspend fun start(device: BluetoothDevice) {
        connectionMutex.withLock {
            stopLocked()
            if (!hasBluetoothConnectPermission()) {
                _state.value = BudsProviderState.Failed("BLUETOOTH_CONNECT not granted")
                return
            }
            _state.value = BudsProviderState.Connecting
            connectionJob =
                scope.launch {
                    var socket: BluetoothSocket? = null
                    try {
                        socket = device.createInsecureRfcommSocketToServiceRecord(BudsAudioProvider.SPP_UUID)
                        budsSocket = socket
                        socket.connect()
                        _state.value = BudsProviderState.Streaming
                        streamAudio(socket)
                    } catch (@Suppress("SwallowedException") security: SecurityException) {
                        _state.value = BudsProviderState.Failed("Bluetooth permission denied")
                    } catch (io: IOException) {
                        _state.value = BudsProviderState.Failed(io.message ?: "Bluetooth socket failed")
                    } finally {
                        runCatching { socket?.close() }
                        if (budsSocket === socket) budsSocket = null
                        if (_state.value is BudsProviderState.Streaming || _state.value is BudsProviderState.Connecting) {
                            _state.value = BudsProviderState.Idle
                        }
                    }
                }
        }
    }

    override suspend fun stop() {
        connectionMutex.withLock {
            stopLocked()
        }
    }

    override fun shutdown() {
        runCatching { budsSocket?.close() }
        budsSocket = null
        connectionJob?.cancel()
        connectionJob = null
        _state.value = BudsProviderState.Idle
    }

    private suspend fun stopLocked() {
        val current = connectionJob
        connectionJob = null
        runCatching { budsSocket?.close() }
        budsSocket = null
        current?.cancelAndJoin()
        _state.value = BudsProviderState.Idle
    }

    private suspend fun streamAudio(socket: BluetoothSocket) {
        val input = socket.inputStream
        val buffer = ByteArray(AUDIO_BUFFER_SIZE)
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            val read = input.read(buffer)
            if (read > 0) {
                _audioData.emit(BudsAudioChunk(buffer.copyOf(read)))
            } else if (read < 0) {
                break
            }
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val AUDIO_BUFFER_SIZE = 0x800
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BudsModule {
    @Binds abstract fun bindBudsAudioProvider(impl: BudsAudioProviderImpl): BudsAudioProvider
}
