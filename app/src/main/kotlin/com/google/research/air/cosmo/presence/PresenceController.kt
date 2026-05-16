package com.google.research.air.cosmo.presence

import com.google.research.air.cosmo.di.ApplicationScope
import com.google.research.air.cosmo.fulfillment.ActionsRepositoryContract
import com.google.research.air.cosmo.fulfillment.FulfillmentAction
import com.google.research.air.cosmo.gatekeeping.GatekeeperOutputSource
import com.google.research.air.cosmo.gatekeeping.GatekeepingResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Singleton
class PresenceController
@Inject
constructor(
    private val actionsRepository: ActionsRepositoryContract,
    private val gatekeeperOutput: GatekeeperOutputSource,
    @ApplicationScope private val scope: CoroutineScope
) : AutoCloseable {
    private val gatekeeperState = MutableStateFlow<GatekeepingResult?>(null)
    private val _state = MutableStateFlow<PresenceState>(PresenceState.Hidden)
    private var gatekeeperJob: Job? = null
    private var stateJob: Job? = null

    val state: StateFlow<PresenceState> = _state

    fun start() {
        if (stateJob != null) return
        gatekeeperJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                gatekeeperOutput.results.collect { result -> gatekeeperState.value = result }
            }
        stateJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                combine(
                    actionsRepository.userFacingActions,
                    actionsRepository.lastFulfillmentId,
                    gatekeeperState,
                    ::buildPresenceState
                ).collect { next -> _state.value = next }
            }
    }

    override fun close() {
        gatekeeperJob?.cancel()
        stateJob?.cancel()
        gatekeeperJob = null
        stateJob = null
        gatekeeperState.value = null
        _state.value = PresenceState.Hidden
    }

    private fun buildPresenceState(
        actions: List<FulfillmentAction>,
        fulfillmentId: String?,
        gatekeeper: GatekeepingResult?
    ): PresenceState {
        if (actions.isNotEmpty()) {
            val cards = actions.map { it.toPresenceCard() }
            return PresenceState.Ready(
                taskId = fulfillmentId ?: actions.first().id,
                cards = cards,
                primaryAction = cards.firstNotNullOfOrNull { card ->
                    card.actions.firstOrNull { it.type == CardActionType.PRIMARY }
                }
            )
        }
        return gatekeeper.toPresenceState()
    }

    private fun GatekeepingResult?.toPresenceState(): PresenceState =
        when {
            this == null -> PresenceState.Hidden
            triggeredFulfillment ->
                PresenceState.Working(
                    taskId = "gatekeeper-$timestampMillis",
                    phase = WorkPhase.PROCESSING,
                    pillText = topSkillLabel()
                )
            detectedSkills.isNotEmpty() -> PresenceState.Parked(PresenceMode.ORB, OrbActivity.LISTENING)
            else -> PresenceState.Hidden
        }

    private fun GatekeepingResult.topSkillLabel(): String =
        skillScores
            .maxByOrNull { it.confidence }
            ?.let { "${it.skillId} ${(it.confidence * 100).roundToInt()}%" }
            ?: "Processing context"
}
