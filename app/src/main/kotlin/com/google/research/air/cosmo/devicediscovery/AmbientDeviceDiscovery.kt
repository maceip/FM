package com.google.research.air.cosmo.devicediscovery

import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AmbientLanDiscoveryContract {
    const val PROTOCOL_VERSION = "cosmo-lan-v1"
    const val ROLE_ANDROID_PEER = "android-peer"
    const val ATTR_PROTOCOL = "protocol"
    const val ATTR_ROLE = "role"
    const val ATTR_CAPABILITIES = "capabilities"
    val COSMO_SERVICE_TYPES = setOf("_cosmo._tcp.", "_cosmo-ambient._tcp.")
}

data class AmbientLanDevice(
    val serviceName: String,
    val serviceType: String,
    val host: InetAddress?,
    val port: Int,
    val attributes: Map<String, String>
) {
    val isCosmoPeer: Boolean
        get() =
            serviceType in AmbientLanDiscoveryContract.COSMO_SERVICE_TYPES ||
                attributes[AmbientLanDiscoveryContract.ATTR_PROTOCOL] == AmbientLanDiscoveryContract.PROTOCOL_VERSION ||
                attributes[AmbientLanDiscoveryContract.ATTR_ROLE] == AmbientLanDiscoveryContract.ROLE_ANDROID_PEER
}

interface AmbientDeviceDiscovery {
    val devices: StateFlow<List<AmbientLanDevice>>

    fun start()

    fun stop()
}

@Singleton
class NsdAmbientDeviceDiscovery
@Inject
constructor(
    @ApplicationContext private val context: Context
) : AmbientDeviceDiscovery {
    private val nsdManager: NsdManager by lazy { context.getSystemService(NsdManager::class.java) }
    private val listeners = mutableMapOf<String, NsdManager.DiscoveryListener>()
    private val discovered = linkedMapOf<String, AmbientLanDevice>()
    private val _devices = MutableStateFlow<List<AmbientLanDevice>>(emptyList())

    override val devices: StateFlow<List<AmbientLanDevice>> = _devices.asStateFlow()

    override fun start() {
        if (listeners.isNotEmpty() || !hasLocalNetworkPermission()) return
        SERVICE_TYPES.forEach { serviceType ->
            val listener =
                object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(regType: String) = Unit

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        resolve(serviceInfo)
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        synchronized(discovered) {
                            discovered.remove(serviceKey(serviceInfo.serviceName, serviceInfo.serviceType))
                            _devices.value = discovered.values.toList()
                        }
                    }

                    override fun onDiscoveryStopped(serviceType: String) = Unit

                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        stopDiscovery(this, serviceType)
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        stopDiscovery(this, serviceType)
                    }
                }
            listeners[serviceType] = listener
            runCatching { nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener) }
                .onFailure { listeners.remove(serviceType) }
        }
    }

    override fun stop() {
        val active = listeners.toMap()
        listeners.clear()
        active.forEach { (serviceType, listener) -> stopDiscovery(listener, serviceType) }
        synchronized(discovered) {
            discovered.clear()
            _devices.value = emptyList()
        }
    }

    private fun resolve(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val device =
                        AmbientLanDevice(
                            serviceName = resolved.serviceName,
                            serviceType = resolved.serviceType,
                            host = resolved.host,
                            port = resolved.port,
                            attributes = resolved.attributes.mapValues { (_, value) -> value.decodeToString() }
                        )
                    synchronized(discovered) {
                        discovered[serviceKey(device.serviceName, device.serviceType)] = device
                        _devices.value = discovered.values.toList()
                    }
                }
            }
        )
    }

    private fun stopDiscovery(
        listener: NsdManager.DiscoveryListener,
        serviceType: String
    ) {
        runCatching { nsdManager.stopServiceDiscovery(listener) }
        listeners.remove(serviceType)
    }

    private fun hasLocalNetworkPermission(): Boolean =
        Build.VERSION.SDK_INT < 36 ||
            ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    private fun serviceKey(
        serviceName: String,
        serviceType: String
    ): String = "$serviceType|$serviceName"

    companion object {
        private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        private val SERVICE_TYPES = AmbientLanDiscoveryContract.COSMO_SERVICE_TYPES + setOf("_meeting._tcp.")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AmbientDeviceDiscoveryModule {
    @Binds abstract fun bindAmbientDeviceDiscovery(impl: NsdAmbientDeviceDiscovery): AmbientDeviceDiscovery
}
