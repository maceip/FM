package com.google.research.air.cosmo.presence

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton state holder for the presence layer. Exposes a [StateFlow] of [PresenceState] that
 * the [PresenceRoot] composable collects, and intent methods that the [FulfillmentOrchestrator]
 * bridge (or manual triggers) call to drive transitions.
 *
 * This class is process-scoped via Hilt [@Singleton]. On process death the in-memory state is lost;
 * the service restores from [DataStore] via [hydrate] on restart.
 */
@Singleton
class PresenceController @Inject constructor() {

    private val _state = MutableStateFlow<PresenceState>(PresenceState.Hidden)
    val state: StateFlow<PresenceState> = _state.asStateFlow()

    fun show(mode: PresenceMode = PresenceMode.TAB) {
        _state.value = PresenceState.Parked(mode, OrbActivity.IDLE)
    }

    fun hide() {
        _state.value = PresenceState.Hidden
    }

    fun setListening() {
        val current = _state.value
        if (current is PresenceState.Parked) {
            _state.value = current.copy(activity = OrbActivity.LISTENING)
        }
    }

    fun attachTask(taskId: String, pillText: String? = null) {
        _state.value = PresenceState.Working(taskId, WorkPhase.STARTED, pillText)
    }

    fun reportPhase(taskId: String, phase: WorkPhase, pillText: String? = null) {
        val current = _state.value
        if (current is PresenceState.Working && current.taskId == taskId) {
            _state.value = current.copy(phase = phase, pillText = pillText ?: current.pillText)
        }
    }

    fun requestInput(taskId: String, prompt: String) {
        _state.value = PresenceState.NeedsInput(taskId, prompt)
    }

    fun complete(taskId: String, cards: List<PresenceCard>, primaryAction: CardAction? = null) {
        _state.value = PresenceState.Ready(taskId, cards, primaryAction)
    }

    fun dismissCurrent() {
        _state.value = PresenceState.Dismissed(Instant.now().plusSeconds(10))
    }

    fun requestExpand() {
        // Expand is handled by the UI layer (tab spring-open, card stack unfurl).
        // Controller just needs to ensure state is in a ready/working state.
    }

    /**
     * Restore state from durable storage after process death. Called by [CosmoPresenceService]
     * on restart. For now, defaults to hidden — wire DataStore persistence when needed.
     */
    fun hydrate() {
        if (_state.value is PresenceState.Hidden) {
            // No-op: start hidden. Future: read last-known state from DataStore.
        }
    }
}
