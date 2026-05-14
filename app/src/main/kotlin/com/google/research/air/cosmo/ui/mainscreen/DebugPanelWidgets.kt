package com.google.research.air.cosmo.ui.mainscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.research.air.cosmo.ephemeralbuffer.EphemeralBufferId
import com.google.research.air.cosmo.ephemeralbuffer.EphemeralBufferRegistry
import com.google.research.air.cosmo.ui.theme.CosmoSignalTag
import com.google.research.air.cosmo.ui.theme.CosmoStickerSurface
import com.google.research.air.cosmo.ui.theme.CosmoTokens

internal typealias ColumnScopeContent = @Composable () -> Unit

@Composable
internal fun DebugCard(
    title: String,
    content: ColumnScopeContent
) {
    val rotation = if (title.hashCode() % 2 == 0) -1f else 1f
    CosmoStickerSurface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        containerColor = CosmoTokens.Color.SurfaceHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(CosmoTokens.Shape.cornerLarge),
        rotationDegrees = rotation
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CosmoSignalTag(
                text = title,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                rotationDegrees = -4f
            )
            content()
        }
    }
}

@Composable
internal fun MetricRow(
    label: String,
    value: String
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ObjectBufferMetric(
    buffers: EphemeralBufferRegistry,
    id: EphemeralBufferId
) {
    val entries by buffers.reader(id).snapshots().collectAsState()
    MetricRow(id.name, entries.size.toString())
}

@Composable
internal fun ByteBufferMetric(
    buffers: EphemeralBufferRegistry,
    id: EphemeralBufferId
) {
    val entries by buffers.byteReader(id).snapshots().collectAsState()
    MetricRow(id.name, entries.size.toString())
}

@Composable
internal fun BufferGroup(
    title: String,
    buffers: EphemeralBufferRegistry,
    token: String
) {
    DebugCard(title) {
        buffers.objectSourceIds().filter { it.name.contains(token) }.sortedBy { it.name }.forEach { ObjectBufferMetric(buffers, it) }
        buffers.byteSourceIds().filter { it.name.contains(token) }.sortedBy { it.name }.forEach { ByteBufferMetric(buffers, it) }
    }
}
