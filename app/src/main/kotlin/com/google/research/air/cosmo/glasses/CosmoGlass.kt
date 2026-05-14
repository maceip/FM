package com.google.research.air.cosmo.glasses

import android.Manifest
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.google.research.air.cosmo.di.ApplicationScope
import com.google.research.air.cosmo.ephemeralbuffer.GlassesCameraEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.logging.CosmosTelemetry
import com.google.research.air.cosmo.logging.TelemetryEvent
import com.google.research.air.cosmo.setting.CosmoSettingStore
import com.google.research.air.cosmo.setting.CosmoState
import com.google.research.air.cosmo.setting.CosmoStateManager
import com.google.research.air.cosmo.ui.theme.CosmoBackground
import com.google.research.air.cosmo.ui.theme.CosmoStickerSurface
import com.google.research.air.cosmo.ui.theme.CosmoTokens
import com.google.research.air.cosmo.ui.theme.setCosmoContent
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Singleton
class CosmoGlassManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val settingStore: CosmoSettingStore,
    private val stateManager: CosmoStateManager,
    @GlassesCameraEphemeralBuffer private val glassCameraBuffer: InMemoryEphemeralBuffer<Any>
) {
    private val _isGlassConnected = MutableStateFlow(false)
    private val _isGlassServiceBound = MutableStateFlow(false)
    private val bridge = ProjectedGlassCaptureBridge(glassCameraBuffer)
    private var serviceConnection: ServiceConnection? = null
    private var glassCaptureService: Any? = null
    private var settingsJob: Job? = null
    private var binding = false
    private var lastFrameProcessTimeMs = 0L

    fun isGlassConnected(): StateFlow<Boolean> = _isGlassConnected

    fun isGlassServiceBound(): StateFlow<Boolean> = _isGlassServiceBound

    fun clearGlassCameraBuffer() = glassCameraBuffer.clear()

    fun startGlassSettingsListener() {
        if (settingsJob?.isActive == true) return
        settingsJob =
            scope.launch {
                settingStore.getCosmoSettingFlow().combine(stateManager.getCosmoState()) { setting, state ->
                    setting.connectToGlassEnabled && state == CosmoState.ENABLED
                }.collect { shouldRun ->
                    if (shouldRun) startProjectedCapture() else stopProjectedCapture()
                }
            }
    }

    fun startProjectedCapture() {
        if (_isGlassServiceBound.value || binding) return
        _isGlassConnected.value = bridge.isProjectedDeviceConnected(context)
        if (!_isGlassConnected.value) return
        startGlassActivity()
        bindGlassCaptureService()
    }

    fun startGlassAudioCapture(onAudioBytes: (ByteArray) -> Unit) {
        bridge.startAudioCapture(glassCaptureService, onAudioBytes)
    }

    fun stopGlassAudioCapture() {
        bridge.stopAudioCapture(glassCaptureService)
    }

    private fun bindGlassCaptureService() {
        if (_isGlassServiceBound.value || binding) return
        binding = true
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    binding = false
                    glassCaptureService = bridge.serviceFromBinder(service)
                    _isGlassServiceBound.value = glassCaptureService != null
                    setupGlassCameraCapture()
                    startGlassCameraCapture()
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    binding = false
                    glassCaptureService = null
                    _isGlassServiceBound.value = false
                }
            }
        serviceConnection = connection
        val bound =
            bridge.bindProjectedGlassCaptureService(
                context = context,
                connection = connection
            )
        binding = bound
        if (!bound) serviceConnection = null
    }

    fun stopProjectedCapture() {
        stopGlassCameraCapture()
        stopGlassAudioCapture()
        serviceConnection?.let { runCatching { context.unbindService(it) } }
        serviceConnection = null
        glassCaptureService = null
        binding = false
        _isGlassServiceBound.value = false
        stopGlassActivity()
    }

    private fun setupGlassCameraCapture() {
        bridge.setupImageCapture(glassCaptureService) { imageProxy ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameProcessTimeMs >= GLASS_FRAME_MIN_INTERVAL_MS) {
                lastFrameProcessTimeMs = now
                bridge.addImageProxyToBuffer(imageProxy)
            } else {
                bridge.closeImageProxy(imageProxy)
            }
        }
    }

    private fun startGlassCameraCapture() {
        bridge.startImageCapture(glassCaptureService)
    }

    private fun stopGlassCameraCapture() {
        bridge.stopImageCapture(glassCaptureService)
    }

    fun startGlassActivity() {
        val intent = Intent(context, CosmoGlassActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val bundle = bridge.createProjectedActivityOptions(context)?.toBundle()
        if (bundle != null) {
            context.startActivity(intent, bundle)
        } else {
            context.startActivity(intent)
        }
    }

    private fun stopGlassActivity() {
        val intent =
            Intent(context, CosmoGlassActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(FINISH_ACTIVITY_EXTRA, true)
        val bundle = bridge.createProjectedActivityOptions(context)?.toBundle()
        if (bundle != null) {
            runCatching { context.startActivity(intent, bundle) }
        } else {
            runCatching { context.startActivity(intent) }
        }
    }

    companion object {
        const val FINISH_ACTIVITY_EXTRA = "finish_activity"
        private const val GLASS_FRAME_MIN_INTERVAL_MS = 10_000L
    }
}

@HiltViewModel
class CosmoGlassViewModel
@Inject
constructor(
    private val manager: CosmoGlassManager,
    private val telemetry: CosmosTelemetry
) : ViewModel() {
    val isConnected = manager.isGlassConnected()
    val isBound = manager.isGlassServiceBound()

    fun clearCameraBuffer() = manager.clearGlassCameraBuffer()

    fun startProjectedCapture() = manager.startProjectedCapture()

    fun recordSession(permissionsGranted: Boolean) {
        telemetry.log(TelemetryEvent.GlassSession(permissionsGranted, isConnected.value, isBound.value))
    }
}

@AndroidEntryPoint
class CosmoGlassActivity : ComponentActivity() {
    private val viewModel: CosmoGlassViewModel by viewModels()
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            render()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra(CosmoGlassManager.FINISH_ACTIVITY_EXTRA, false)) {
            finish()
            return
        }
        if (!arePermissionsGranted()) {
            permissionLauncher.launch(requiredPermissions)
        }
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(CosmoGlassManager.FINISH_ACTIVITY_EXTRA, false)) finish()
    }

    private fun arePermissionsGranted(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun render() {
        val permissionsGranted = arePermissionsGranted()
        if (permissionsGranted) {
            viewModel.startProjectedCapture()
        }
        viewModel.recordSession(permissionsGranted)
        setCosmoContent {
            val isConnected by viewModel.isConnected.collectAsState()
            val isBound by viewModel.isBound.collectAsState()
            GlassScreen(
                permissionsGranted = permissionsGranted,
                isConnected = isConnected,
                isBound = isBound,
                onClearCameraBuffer = viewModel::clearCameraBuffer
            )
        }
    }

    companion object {
        val requiredPermissions: Array<String>
            get() =
                buildList {
                    add(Manifest.permission.CAMERA)
                    add(Manifest.permission.RECORD_AUDIO)
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }.toTypedArray()
    }
}

@Composable
private fun GlassScreen(
    permissionsGranted: Boolean,
    isConnected: Boolean,
    isBound: Boolean,
    onClearCameraBuffer: () -> Unit
) {
    CosmoBackground(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(CosmoTokens.Spacing.glassScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (permissionsGranted) "Glass capture ready" else "Glass permissions required", style = MaterialTheme.typography.headlineSmall)
            CosmoStickerSurface(
                modifier = Modifier.fillMaxWidth(),
                containerColor = CosmoTokens.Color.SurfaceHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(CosmoTokens.Shape.cornerLarge)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Connected: $isConnected")
                    Text("Service bound: $isBound")
                    Text("Camera and microphone permissions are used only for live Glass capture.")
                }
            }
            Button(onClick = onClearCameraBuffer) {
                Text("Clear Glass camera buffer")
            }
        }
    }
}

private class ProjectedGlassCaptureBridge(
    private val glassCameraBuffer: InMemoryEphemeralBuffer<Any>
) {
    fun isProjectedDeviceConnected(context: Context): Boolean =
        runCatching {
            val clazz = Class.forName(PROJECTED_CONTEXT_CLASS)
            val method = clazz.methods.firstOrNull {
                it.name == "isProjectedDeviceConnected" && it.parameterTypes.contentEquals(arrayOf(Context::class.java))
            } ?: return@runCatching false
            method.invoke(null, context) as? Boolean ?: false
        }.getOrDefault(false)

    fun createProjectedActivityOptions(context: Context): ActivityOptions? =
        runCatching {
            val clazz = Class.forName(PROJECTED_CONTEXT_CLASS)
            val method = clazz.methods.firstOrNull {
                it.name == "createProjectedActivityOptions" && it.parameterTypes.contentEquals(arrayOf(Context::class.java))
            } ?: return@runCatching null
            method.invoke(null, context) as? ActivityOptions
        }.getOrNull()

    fun bindProjectedGlassCaptureService(
        context: Context,
        connection: ServiceConnection
    ): Boolean =
        PROJECTED_CAPTURE_PACKAGES.any { packageName ->
            val intent = Intent().setClassName(packageName, PROJECTED_CAPTURE_SERVICE_CLASS)
            runCatching { context.bindService(intent, connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        }

    fun serviceFromBinder(binder: IBinder): Any? =
        runCatching {
            binder.javaClass.methods.firstOrNull { it.name == "getService" && it.parameterCount == 0 }?.invoke(binder)
        }.getOrNull()

    fun setupImageCapture(
        service: Any?,
        onImageProxy: (Any) -> Unit
    ) {
        if (service == null) return
        val callback: (Any) -> Unit = onImageProxy
        runCatching {
            val defaultMethod = service.javaClass.methods.firstOrNull { it.name == "setupImageCapture\$default" && it.parameterCount == 8 }
            if (defaultMethod != null) {
                defaultMethod.invoke(null, service, 0, 0, 0, 0, callback, DEFAULT_IMAGE_CAPTURE_MASK, null)
                return
            }
            val method = service.javaClass.methods.firstOrNull { it.name == "setupImageCapture" && it.parameterCount == 5 }
            method?.invoke(service, 0, 0, 0, 0, callback)
        }
    }

    fun startImageCapture(service: Any?) {
        invokeNoArg(service, "startImageCapture")
    }

    fun stopImageCapture(service: Any?) {
        invokeNoArg(service, "stopImageCapture")
    }

    fun startAudioCapture(
        service: Any?,
        onAudioBytes: (ByteArray) -> Unit
    ) {
        if (service == null) return
        runCatching {
            val configClass = Class.forName(PROJECTED_AUDIO_CONFIG_CLASS)
            val audioConfig = configClass.getDeclaredConstructor().newInstance()
            val callback: (ByteArray, Int) -> Unit = { bytes, _ -> onAudioBytes(bytes) }
            val method = service.javaClass.methods.firstOrNull { it.name == "startAudioCapture" && it.parameterCount == 2 }
            method?.invoke(service, audioConfig, callback)
        }
    }

    fun stopAudioCapture(service: Any?) {
        invokeNoArg(service, "stopAudioCapture")
    }

    fun addImageProxyToBuffer(imageProxy: Any) {
        val bitmap = runCatching {
            imageProxy.javaClass.methods.firstOrNull { it.name == "toBitmap" && it.parameterCount == 0 }?.invoke(imageProxy)
        }.getOrNull()
        if (bitmap != null) {
            glassCameraBuffer.add(bitmap)
        }
        closeImageProxy(imageProxy)
    }

    fun closeImageProxy(imageProxy: Any) {
        invokeNoArg(imageProxy, "close")
    }

    private fun invokeNoArg(
        target: Any?,
        methodName: String
    ) {
        if (target == null) return
        runCatching {
            target.javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 0 }?.invoke(target)
        }
    }

    companion object {
        private const val PROJECTED_CONTEXT_CLASS = "androidx.xr.projected.ProjectedContext"
        private const val PROJECTED_CAPTURE_SERVICE_CLASS =
            "com.google.android.glasses.shared.ai.experimental.apis.projectedcapture.service.ProjectedGlassCaptureService"
        private const val PROJECTED_AUDIO_CONFIG_CLASS =
            "com.google.android.glasses.shared.ai.experimental.apis.projectedcapture.audio.ProjectedAudioCaptureManager\$AudioConfig"
        private const val DEFAULT_IMAGE_CAPTURE_MASK = 0x0f
        private val PROJECTED_CAPTURE_PACKAGES =
            listOf(
                "com.google.android.glasses.shared.ai.experimental.apis.projectedcapture",
                "com.google.android.glasses.core",
                "com.google.android.projection.core"
            )
    }
}
