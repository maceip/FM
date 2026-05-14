package com.google.research.air.cosmo.ui.mainscreen

import androidx.compose.runtime.Composable
import com.google.research.air.cosmo.ephemeralbuffer.EphemeralBufferRegistry
import javax.inject.Inject

class BufferOverviewDebugPanel
@Inject
constructor(
    private val buffers: EphemeralBufferRegistry
) : BaseDebugPanel(14, "buffer_overview", "Buffer overview") {
    @Composable override fun Content() {
        DebugCard(title) {
            MetricRow("Object buffers", buffers.objectSourceIds().size.toString())
            MetricRow("Writable object buffers", buffers.writableObjectSourceIds().size.toString())
            MetricRow("Byte buffers", buffers.byteSourceIds().size.toString())
        }
    }
}

class ObjectBuffersDebugPanel
@Inject
constructor(
    private val buffers: EphemeralBufferRegistry
) : BaseDebugPanel(15, "object_buffers", "Object buffer sizes") {
    @Composable override fun Content() {
        DebugCard(title) {
            buffers.objectSourceIds().sortedBy { it.name }.forEach { id ->
                ObjectBufferMetric(buffers, id)
            }
        }
    }
}

class ByteBuffersDebugPanel
@Inject
constructor(
    private val buffers: EphemeralBufferRegistry
) : BaseDebugPanel(16, "byte_buffers", "Byte buffer sizes") {
    @Composable override fun Content() {
        DebugCard(title) {
            buffers.byteSourceIds().sortedBy { it.name }.forEach { id ->
                ByteBufferMetric(buffers, id)
            }
        }
    }
}

class AudioBuffersDebugPanel
@Inject
constructor(
    private val buffers: EphemeralBufferRegistry
) : BaseDebugPanel(17, "audio_buffers", "Audio buffers") {
    @Composable override fun Content() {
        BufferGroup(title, buffers, "AUDIO")
    }
}

class ScreenBuffersDebugPanel
@Inject
constructor(
    private val buffers: EphemeralBufferRegistry
) : BaseDebugPanel(18, "screen_buffers", "Screen buffers") {
    @Composable override fun Content() {
        BufferGroup(title, buffers, "SCREEN")
    }
}

class CameraBuffersDebugPanel
@Inject
constructor(
    private val buffers: EphemeralBufferRegistry
) : BaseDebugPanel(19, "camera_buffers", "Camera buffers") {
    @Composable override fun Content() {
        BufferGroup(title, buffers, "CAMERA")
    }
}

class NotificationBuffersDebugPanel
@Inject
constructor(
    private val buffers: EphemeralBufferRegistry
) : BaseDebugPanel(20, "notification_buffers", "Notification buffers") {
    @Composable override fun Content() {
        BufferGroup(title, buffers, "NOTIFICATION")
    }
}

class LocationBuffersDebugPanel
@Inject
constructor(
    private val buffers: EphemeralBufferRegistry
) : BaseDebugPanel(21, "location_buffers", "Location buffers") {
    @Composable override fun Content() {
        BufferGroup(title, buffers, "LOCATION")
    }
}
