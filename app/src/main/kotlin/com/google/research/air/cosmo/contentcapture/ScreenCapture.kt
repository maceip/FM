package com.google.research.air.cosmo.contentcapture

import com.google.research.air.cosmo.accessibility.AccessibilityEventSource
import com.google.research.air.cosmo.context.ContextEvent
import com.google.research.air.cosmo.context.ContextStore
import com.google.research.air.cosmo.context.RetentionClass
import com.google.research.air.cosmo.context.Source
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.ScreenEphemeralBuffer
import com.google.research.air.cosmo.setting.CosmoSettingStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class ScreenObservation(
    val timestampMillis: Long,
    val packageName: String,
    val text: String,
    val viewHierarchy: String
)

interface PeriodicScreenCaptor {
    fun startCapture(): Flow<ScreenObservation>
}

class PeriodicScreenCaptorImpl
@Inject
constructor(
    private val accessibilityServiceProvider: AccessibilityEventSource,
    private val settings: CosmoSettingStore,
    private val contextStore: ContextStore,
    @ScreenEphemeralBuffer private val buffer: InMemoryEphemeralBuffer<Any>
) : PeriodicScreenCaptor {
    override fun startCapture(): Flow<ScreenObservation> = flow {
        while (true) {
            if (settings.isScreenContextEnabled()) {
                val observation = accessibilityServiceProvider.activeScreenObservation()
                if (observation != null && observation.text.isNotBlank()) {
                    buffer.add(observation, observation.timestampMillis)
                    contextStore.insert(
                        ContextEvent(
                            id = UUID.randomUUID().toString(),
                            source = Source.SCREEN,
                            timestampMs = observation.timestampMillis,
                            text = observation.text,
                            blobRef = null,
                            embedding = null,
                            retentionClass = RetentionClass.EPHEMERAL,
                            sourceMetadata = mapOf("package" to observation.packageName)
                        )
                    )
                    emit(observation)
                }
            }
            delay(10_000)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ContentCaptureModule {
    @Binds abstract fun bindPeriodicScreenCaptor(impl: PeriodicScreenCaptorImpl): PeriodicScreenCaptor
}
