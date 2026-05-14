package com.google.research.air.cosmo.fulfillment

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface ActionsRepositoryContract {
    val currentActions: StateFlow<List<FulfillmentAction>>
    val userFacingActions: StateFlow<List<FulfillmentAction>>
    val executedActions: StateFlow<List<FulfillmentAction>>
    val userDismissedActions: StateFlow<List<FulfillmentAction>>
    val cosmoRemovedActions: StateFlow<List<FulfillmentAction>>
    val fulfillmentHistory: StateFlow<List<FulfillmentDebugInfo>>
    val lastFulfillmentId: StateFlow<String?>
    val hasNewActions: SharedFlow<Unit>

    fun addFulfillmentHistory(info: FulfillmentDebugInfo)

    fun getFulfillmentDebugInfo(id: String): FulfillmentDebugInfo?

    fun listFulfillmentHistory(id: String): List<FulfillmentDebugInfo>

    fun clearAllDebugInfos()

    fun clearAllActions()

    fun maybeUpdateActionsList(
        actions: List<FulfillmentAction>,
        fulfillmentId: String
    )

    fun markActionAsExecuted(id: String)

    fun markActionAsDismissed(id: String)
}

@Singleton
class ActionsRepository
@Inject
constructor() : ActionsRepositoryContract {
    private val _currentActions = MutableStateFlow<List<FulfillmentAction>>(emptyList())
    private val _userFacingActions = MutableStateFlow<List<FulfillmentAction>>(emptyList())
    private val _executedActions = MutableStateFlow<List<FulfillmentAction>>(emptyList())
    private val _userDismissedActions = MutableStateFlow<List<FulfillmentAction>>(emptyList())
    private val _cosmoRemovedActions = MutableStateFlow<List<FulfillmentAction>>(emptyList())
    private val _history = MutableStateFlow<List<FulfillmentDebugInfo>>(emptyList())
    private val _lastFulfillmentId = MutableStateFlow<String?>(null)
    private val _hasNewActions = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    override val currentActions: StateFlow<List<FulfillmentAction>> = _currentActions
    override val userFacingActions: StateFlow<List<FulfillmentAction>> = _userFacingActions
    override val executedActions: StateFlow<List<FulfillmentAction>> = _executedActions
    override val userDismissedActions: StateFlow<List<FulfillmentAction>> = _userDismissedActions
    override val cosmoRemovedActions: StateFlow<List<FulfillmentAction>> = _cosmoRemovedActions
    override val fulfillmentHistory: StateFlow<List<FulfillmentDebugInfo>> = _history
    override val lastFulfillmentId: StateFlow<String?> = _lastFulfillmentId
    override val hasNewActions: SharedFlow<Unit> = _hasNewActions

    override fun addFulfillmentHistory(info: FulfillmentDebugInfo) {
        _history.value = (_history.value + info).takeLast(100)
    }

    override fun getFulfillmentDebugInfo(id: String): FulfillmentDebugInfo? = _history.value.lastOrNull { it.fulfillmentId == id }

    override fun listFulfillmentHistory(id: String): List<FulfillmentDebugInfo> = _history.value.filter { it.fulfillmentId == id }

    override fun clearAllDebugInfos() {
        _history.value = emptyList()
    }

    override fun clearAllActions() {
        _currentActions.value = emptyList()
        _userFacingActions.value = emptyList()
        _executedActions.value = emptyList()
        _userDismissedActions.value = emptyList()
        _cosmoRemovedActions.value = emptyList()
        _lastFulfillmentId.value = null
    }

    override fun maybeUpdateActionsList(
        actions: List<FulfillmentAction>,
        fulfillmentId: String
    ) {
        _lastFulfillmentId.value = fulfillmentId
        val removed = actions.filterIsInstance<FulfillmentAction.RemovedAction>()
        val removedIds = removed.mapTo(mutableSetOf()) { it.id }
        val active = actions.filterNot { it is FulfillmentAction.RemovedAction || it.id in removedIds }
        _cosmoRemovedActions.value = _cosmoRemovedActions.value + removed
        _currentActions.value = (_currentActions.value.filterNot { existing -> actions.any { it.id == existing.id } } + active)
        _userFacingActions.value = _currentActions.value.filter { it.status != FulfillmentAction.Status.REMOVED }
        if (active.isNotEmpty()) _hasNewActions.tryEmit(Unit)
    }

    override fun markActionAsExecuted(id: String) {
        retireAction(id, FulfillmentAction.Status.EXECUTED)
    }

    override fun markActionAsDismissed(id: String) {
        retireAction(id, FulfillmentAction.Status.DISMISSED)
    }

    private fun retireAction(
        id: String,
        status: FulfillmentAction.Status
    ) {
        val match = _currentActions.value.firstOrNull { it.id == id } ?: return
        _currentActions.value = _currentActions.value.filterNot { it.id == id }
        _userFacingActions.value = _userFacingActions.value.filterNot { it.id == id }
        if (status == FulfillmentAction.Status.EXECUTED) _executedActions.value = _executedActions.value + match
        if (status == FulfillmentAction.Status.DISMISSED) _userDismissedActions.value = _userDismissedActions.value + match
    }
}
