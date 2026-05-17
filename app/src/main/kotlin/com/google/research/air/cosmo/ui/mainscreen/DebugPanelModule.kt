package com.google.research.air.cosmo.ui.mainscreen

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugPanelModule {
    @Binds @IntoSet abstract fun bindControllerPanel(impl: ControllerDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindSettingsPanel(impl: SettingsDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindPermissionsPanel(impl: PermissionsDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindLlmStatsPanel(impl: LlmStatsDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindCurrentActionsPanel(impl: CurrentActionsDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindUserFacingActionsPanel(impl: UserFacingActionsDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindExecutedActionsPanel(impl: ExecutedActionsDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindDismissedActionsPanel(impl: DismissedActionsDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindRemovedActionsPanel(impl: RemovedActionsDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindFulfillmentHistoryPanel(impl: FulfillmentHistoryDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindLastFulfillmentPanel(impl: LastFulfillmentDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindGatekeeperLivePanel(impl: GatekeeperLiveDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindGatekeeperScoresPanel(impl: GatekeeperScoresDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindBufferOverviewPanel(impl: BufferOverviewDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindObjectBuffersPanel(impl: ObjectBuffersDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindByteBuffersPanel(impl: ByteBuffersDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindAudioBuffersPanel(impl: AudioBuffersDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindScreenBuffersPanel(impl: ScreenBuffersDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindCameraBuffersPanel(impl: CameraBuffersDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindNotificationBuffersPanel(impl: NotificationBuffersDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindLocationBuffersPanel(impl: LocationBuffersDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindSkillsPanel(impl: SkillsDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindModelAssetsPanel(impl: ModelAssetsDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindObservabilitySchemaPanel(impl: ObservabilitySchemaDebugPanel): CosmoDebugPanel
    @Binds @IntoSet abstract fun bindPresenceOverlayPanel(impl: PresenceOverlayDebugPanel): CosmoDebugPanel
}
