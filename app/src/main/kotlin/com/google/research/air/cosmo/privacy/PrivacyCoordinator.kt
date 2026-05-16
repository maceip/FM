package com.google.research.air.cosmo.privacy

import com.google.research.air.cosmo.context.ContextStore
import com.google.research.air.cosmo.context.Source
import com.google.research.air.cosmo.ephemeralbuffer.EphemeralBufferRegistry
import com.google.research.air.cosmo.setting.CosmoSettingStore
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

data class PrivacyDeletionResult(
    val contextRowsDeleted: Int,
    val buffersChanged: Int
)

@Singleton
class PrivacyCoordinator
@Inject
constructor(
    private val contextStore: ContextStore,
    private val buffers: EphemeralBufferRegistry,
    private val settings: CosmoSettingStore,
    private val clock: Clock
) {
    suspend fun sweepRetention(): PrivacyDeletionResult {
        val retentionMillis = settings.getEphemeralBufferDurationMin().coerceAtLeast(1) * 60_000L
        return forgetWindow(Long.MIN_VALUE..(clock.millis() - retentionMillis))
    }

    suspend fun forgetWindow(
        range: LongRange,
        sources: Set<Source>? = null
    ): PrivacyDeletionResult =
        PrivacyDeletionResult(
            contextRowsDeleted = contextStore.deleteRange(range, sources),
            buffersChanged = buffers.removeRange(range)
        )
}
