package com.google.research.air.cosmo.interactioncapture

import android.view.accessibility.AccessibilityEvent
import com.google.research.air.cosmo.accessibility.AccessibilityEventSource
import com.google.research.air.cosmo.accessibility.AccessibilityEventListener
import com.google.research.air.cosmo.context.ContextEvent
import com.google.research.air.cosmo.context.ContextStore
import com.google.research.air.cosmo.context.RetentionClass
import com.google.research.air.cosmo.context.Source
import com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer
import com.google.research.air.cosmo.ephemeralbuffer.ScreenInteractionEphemeralBuffer
import com.google.research.air.cosmo.setting.CosmoSettingStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking

enum class InteractionType { CLICK, FOCUS, SCROLL, SWITCH_APPLICATION, TEXT_CHANGED }

data class ScreenInteraction(
    val timestampMillis: Long,
    val type: InteractionType,
    val packageName: String,
    val windowId: Int,
    val text: String
)

interface ScreenInteractionCaptor {
    fun startCapture(): Flow<ScreenInteraction>
}

class ScreenInteractionCaptorImpl
@Inject
constructor(
    private val provider: AccessibilityEventSource,
    private val settings: CosmoSettingStore,
    private val contextStore: ContextStore,
    @ScreenInteractionEphemeralBuffer private val buffer: InMemoryEphemeralBuffer<Any>
) : ScreenInteractionCaptor {
    override fun startCapture(): Flow<ScreenInteraction> = callbackFlow {
        val listener =
            AccessibilityEventListener { event ->
                if (!settings.isScreenInteractionCaptureEnabled()) return@AccessibilityEventListener
                val interaction = event.toInteraction() ?: return@AccessibilityEventListener
                buffer.add(interaction, interaction.timestampMillis)
                runBlocking {
                    contextStore.insert(
                        ContextEvent(
                            id = UUID.randomUUID().toString(),
                            source = Source.INTERACTION,
                            timestampMs = interaction.timestampMillis,
                            text = "${interaction.type}: ${interaction.text}",
                            blobRef = null,
                            embedding = null,
                            retentionClass = RetentionClass.EPHEMERAL,
                            sourceMetadata =
                            mapOf(
                                "package" to interaction.packageName,
                                "window_id" to interaction.windowId.toString()
                            )
                        )
                    )
                }
                trySend(interaction)
            }
        provider.addListener(listener)
        awaitClose { provider.removeListener(listener) }
    }

    private fun AccessibilityEvent.toInteraction(): ScreenInteraction? {
        val type =
            when (eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> InteractionType.CLICK
                AccessibilityEvent.TYPE_VIEW_FOCUSED -> InteractionType.FOCUS
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> InteractionType.SCROLL
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> InteractionType.SWITCH_APPLICATION
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> InteractionType.TEXT_CHANGED
                else -> return null
            }
        return ScreenInteraction(
            timestampMillis = eventTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
            type = type,
            packageName = packageName?.toString().orEmpty(),
            windowId = windowId,
            text = text.joinToString(" ")
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class InteractionCaptureModule {
    @Binds abstract fun bindScreenInteractionCaptor(impl: ScreenInteractionCaptorImpl): ScreenInteractionCaptor
}
