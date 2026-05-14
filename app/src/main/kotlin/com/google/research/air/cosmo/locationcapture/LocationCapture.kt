package com.google.research.air.cosmo.locationcapture

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.research.air.cosmo.context.ContextEvent
import com.google.research.air.cosmo.context.ContextStore
import com.google.research.air.cosmo.context.RetentionClass
import com.google.research.air.cosmo.context.Source
import com.google.research.air.cosmo.di.ApplicationScope
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.LocationEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.LocationTextEphemeralBuffer
import com.google.research.air.cosmo.setting.CosmoSettingStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

data class LocationObservation(
    val timestampMillis: Long,
    val provider: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
    val bearingDegrees: Float?
) {
    fun toContextText(): String =
        buildString {
            append(latitude)
            append(",")
            append(longitude)
            if (accuracyMeters != null) append(" +/- ").append(accuracyMeters).append("m")
        }
}

interface LocationCaptor {
    fun startCapture(): Flow<LocationObservation>

    fun lastKnown(): LocationObservation?
}

class LocationCaptorImpl
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val settings: CosmoSettingStore,
    private val contextStore: ContextStore,
    @LocationEphemeralBuffer private val locationBuffer: InMemoryEphemeralBuffer<Any>,
    @LocationTextEphemeralBuffer private val locationTextBuffer: InMemoryEphemeralBuffer<Any>
) : LocationCaptor {
    private val locationManager: LocationManager by lazy { context.getSystemService(LocationManager::class.java) }

    @SuppressLint("MissingPermission")
    override fun startCapture(): Flow<LocationObservation> = callbackFlow {
        if (!hasLocationPermission()) {
            close()
            return@callbackFlow
        }
        val listener =
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!settings.isLocationCaptureEnabled()) return
                    val observation = location.toObservation()
                    locationBuffer.add(observation, observation.timestampMillis)
                    locationTextBuffer.add(observation.toContextText(), observation.timestampMillis)
                    scope.launch {
                        contextStore.insert(
                            ContextEvent(
                                id = UUID.randomUUID().toString(),
                                source = Source.LOCATION,
                                timestampMs = observation.timestampMillis,
                                text = observation.toContextText(),
                                blobRef = null,
                                embedding = null,
                                retentionClass = RetentionClass.EPHEMERAL,
                                sourceMetadata =
                                    mapOf(
                                        "provider" to observation.provider,
                                        "accuracy_meters" to observation.accuracyMeters.orZeroString()
                                    )
                            )
                        )
                    }
                    trySend(observation)
                }

                override fun onProviderEnabled(provider: String) = Unit

                override fun onProviderDisabled(provider: String) = Unit
            }
        activeProviders().forEach { provider ->
            locationManager.requestLocationUpdates(provider, MIN_TIME_MILLIS, MIN_DISTANCE_METERS, listener)
        }
        awaitClose { locationManager.removeUpdates(listener) }
    }

    @SuppressLint("MissingPermission")
    override fun lastKnown(): LocationObservation? {
        if (!hasLocationPermission()) return null
        return activeProviders()
            .mapNotNull { provider -> locationManager.getLastKnownLocation(provider) }
            .maxByOrNull { it.time }
            ?.toObservation()
    }

    private fun activeProviders(): List<String> =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider -> runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false) }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun Location.toObservation(): LocationObservation =
        LocationObservation(
            timestampMillis = time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            provider = provider.orEmpty(),
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) accuracy else null,
            altitudeMeters = if (hasAltitude()) altitude else null,
            bearingDegrees = if (hasBearing()) bearing else null
        )

    private fun Float?.orZeroString(): String = this?.toString() ?: "0"

    companion object {
        private const val MIN_TIME_MILLIS = 60_000L
        private const val MIN_DISTANCE_METERS = 25f
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationCaptureModule {
    @Binds abstract fun bindLocationCaptor(impl: LocationCaptorImpl): LocationCaptor
}
