package com.google.research.air.cosmo.lib.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.research.air.cosmo.setting.proto.CosmoSetting
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class PermissionRequestDataStore

private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
private const val ACCESS_LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

enum class RuntimePermissionGrantState {
    GRANTED,
    NOT_REQUESTED,
    SHOULD_SHOW_RATIONALE,
    DENIED_PERMANENTLY
}

enum class RuntimePermissionDescriptor(
    val manifestPermission: String,
    val minSdk: Int,
    val title: String,
    val rationale: String
) {
    Microphone(
        Manifest.permission.RECORD_AUDIO,
        Build.VERSION_CODES.BASE,
        "Microphone",
        "Captures enabled ambient audio and voice prompts."
    ),
    Camera(
        Manifest.permission.CAMERA,
        Build.VERSION_CODES.BASE,
        "Camera",
        "Captures enabled device or glasses camera frames for visual questions."
    ),
    Notifications(
        POST_NOTIFICATIONS_PERMISSION,
        Build.VERSION_CODES.TIRAMISU,
        "Notifications",
        "Shows capture, recording, and background processing status."
    ),
    FineLocation(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Build.VERSION_CODES.BASE,
        "Precise location",
        "Adds location context only when location capture is enabled."
    ),
    ApproximateLocation(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Build.VERSION_CODES.BASE,
        "Approximate location",
        "Adds coarse place context when precise location is not required."
    ),
    CalendarRead(
        Manifest.permission.READ_CALENDAR,
        Build.VERSION_CODES.BASE,
        "Read calendar",
        "Reads event context before drafting calendar-aware answers."
    ),
    CalendarWrite(
        Manifest.permission.WRITE_CALENDAR,
        Build.VERSION_CODES.BASE,
        "Write calendar",
        "Creates calendar drafts only after explicit user confirmation."
    ),
    PhoneCall(
        Manifest.permission.CALL_PHONE,
        Build.VERSION_CODES.BASE,
        "Phone calls",
        "Starts confirmed call actions from supported skills."
    ),
    NearbyDevices(
        Manifest.permission.BLUETOOTH_CONNECT,
        Build.VERSION_CODES.S,
        "Nearby devices",
        "Connects to supported watch, buds, and glasses capture surfaces."
    ),
    LocalNetwork(
        ACCESS_LOCAL_NETWORK_PERMISSION,
        36,
        "Local network",
        "Discovers meeting-room and LAN devices only when device controls are enabled."
    );

    fun isAvailableOnDevice(): Boolean = Build.VERSION.SDK_INT >= minSdk
}

data class RuntimePermissionState(
    val descriptor: RuntimePermissionDescriptor,
    val grantState: RuntimePermissionGrantState
) {
    val manifestPermission: String = descriptor.manifestPermission
}

@Singleton
class PermissionRationaleHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @PermissionRequestDataStore private val requestStore: DataStore<Preferences>
) {
    suspend fun states(
        activity: Activity,
        permissions: Collection<RuntimePermissionDescriptor> = RuntimePermissionDescriptor.entries
    ): List<RuntimePermissionState> {
        val requested = requestStore.data.first()
        return permissions
            .filter { it.isAvailableOnDevice() }
            .distinctBy { it.manifestPermission }
            .map { descriptor ->
                RuntimePermissionState(
                    descriptor = descriptor,
                    grantState = grantState(activity, descriptor, requested)
                )
            }
    }

    suspend fun markRequested(manifestPermissions: Collection<String>) {
        val active = manifestPermissions.toSet()
        requestStore.edit { preferences ->
            RuntimePermissionDescriptor.entries
                .filter { it.manifestPermission in active }
                .forEach { preferences[requestedKey(it.manifestPermission)] = true }
        }
    }

    fun capturePermissions(setting: CosmoSetting): List<RuntimePermissionDescriptor> = buildList {
        if (setting.ambientAudioEnabled) add(RuntimePermissionDescriptor.Microphone)
        if (setting.cameraEnabled || setting.glassesCameraEnabled) add(RuntimePermissionDescriptor.Camera)
        if (setting.notificationCaptureEnabled) add(RuntimePermissionDescriptor.Notifications)
        if (setting.locationCaptureEnabled) {
            add(RuntimePermissionDescriptor.FineLocation)
            add(RuntimePermissionDescriptor.ApproximateLocation)
        }
        if (setting.connectToGlassEnabled || setting.watchAudioEnabled || setting.budsAudioEnabled) {
            add(RuntimePermissionDescriptor.NearbyDevices)
        }
        if (setting.deviceControlEnabled) add(RuntimePermissionDescriptor.LocalNetwork)
    }

    fun appSettingsIntent(): Intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun grantState(
        activity: Activity,
        descriptor: RuntimePermissionDescriptor,
        requested: Preferences
    ): RuntimePermissionGrantState {
        if (ContextCompat.checkSelfPermission(context, descriptor.manifestPermission) == PackageManager.PERMISSION_GRANTED) {
            return RuntimePermissionGrantState.GRANTED
        }
        if (activity.shouldShowRequestPermissionRationale(descriptor.manifestPermission)) {
            return RuntimePermissionGrantState.SHOULD_SHOW_RATIONALE
        }
        return if (requested[requestedKey(descriptor.manifestPermission)] == true) {
            RuntimePermissionGrantState.DENIED_PERMANENTLY
        } else {
            RuntimePermissionGrantState.NOT_REQUESTED
        }
    }

    private fun requestedKey(permission: String): Preferences.Key<Boolean> = booleanPreferencesKey("requested_${permission.substringAfterLast('.')}")
}

@Module
@InstallIn(SingletonComponent::class)
object PermissionRationaleModule {
    @Provides
    @Singleton
    @PermissionRequestDataStore
    fun providePermissionRequestDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("permission_requests.preferences_pb") }
    )
}
