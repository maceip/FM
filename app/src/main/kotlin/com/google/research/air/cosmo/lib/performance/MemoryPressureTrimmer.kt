package com.google.research.air.cosmo.lib.performance

import android.content.ComponentCallbacks2
import com.google.research.air.cosmo.ephemeralbuffer.EphemeralBufferRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryPressureTrimmer
@Inject
constructor(
    private val buffers: EphemeralBufferRegistry
) {
    @Suppress("DEPRECATION")
    fun trim(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> clearByteBuffers()
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> clearAllBuffers()
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> clearByteBuffers()
        }
    }

    private fun clearByteBuffers() {
        buffers.byteSourceIds().forEach { id -> buffers.byteWriter(id).clear() }
    }

    private fun clearAllBuffers() {
        buffers.byteSourceIds().forEach { id -> buffers.byteWriter(id).clear() }
        buffers.writableObjectSourceIds().forEach { id -> buffers.writer(id).clear() }
    }
}
