package com.google.research.air.cosmo.voiceassistant.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.research.air.cosmo.context.ContextEvent
import com.google.research.air.cosmo.context.ContextStore
import com.google.research.air.cosmo.context.RetentionClass
import com.google.research.air.cosmo.context.Source
import com.google.research.air.cosmo.di.BackgroundExecutor
import com.google.research.air.cosmo.ephemeralbuffer.DeviceCameraEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

class CameraBitmapAnalyzer(
    private val listener: (Bitmap, Long) -> Unit
) : ImageAnalysis.Analyzer {
    private var lastFrameSentTimestamp: Long = 0

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        try {
            if (now - lastFrameSentTimestamp >= FRAME_THROTTLE_MS) {
                listener(image.toBitmapCompat(), now)
                lastFrameSentTimestamp = now
            }
        } finally {
            image.close()
        }
    }

    companion object {
        const val FRAME_THROTTLE_MS = 10_000L
    }
}

interface DeviceCameraCaptureSurface {
    @Composable
    fun CameraPreview(modifier: Modifier = Modifier)
}

class CameraViewComposable
@Inject
constructor(
    @BackgroundExecutor
    private val executor: Executor,
    @DeviceCameraEphemeralBuffer private val buffer: InMemoryEphemeralBuffer<Any>,
    private val contextStore: ContextStore
) : DeviceCameraCaptureSurface {
    @Composable
    override fun CameraPreview(modifier: Modifier) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview =
                                Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                            val analyzer =
                                ImageAnalysis
                                    .Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { analysis ->
                                        analysis.setAnalyzer(
                                            executor,
                                            CameraBitmapAnalyzer { bitmap, timestamp ->
                                                buffer.add(bitmap, timestamp)
                                                val ref = bitmap.persist(context, timestamp)
                                                runBlocking {
                                                    contextStore.insert(
                                                        ContextEvent(
                                                            id = UUID.randomUUID().toString(),
                                                            source = Source.CAMERA,
                                                            timestampMs = timestamp,
                                                            text = "Device camera frame ${bitmap.width}x${bitmap.height}",
                                                            blobRef = ref,
                                                            embedding = null,
                                                            retentionClass = RetentionClass.EPHEMERAL,
                                                            sourceMetadata = mapOf("camera" to "device")
                                                        )
                                                    )
                                                }
                                            }
                                        )
                                    }
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
                        }, executor)
                    }
                }
            }
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CameraCaptureModule {
    @Binds abstract fun bindDeviceCameraCaptureSurface(impl: CameraViewComposable): DeviceCameraCaptureSurface
}

private fun Bitmap.persist(
    context: Context,
    timestamp: Long
): String {
    val dir = File(context.cacheDir, "camera_frames").apply { mkdirs() }
    val file = File(dir, "$timestamp.jpg")
    FileOutputStream(file).use { compress(Bitmap.CompressFormat.JPEG, 82, it) }
    return file.absolutePath
}

private fun ImageProxy.toBitmapCompat(): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    val out = ByteArrayOutputStream()
    YuvImage(nv21, ImageFormat.NV21, width, height, null)
        .compressToJpeg(Rect(0, 0, width, height), 82, out)
    return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
}
