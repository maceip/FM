package com.google.research.air.cosmo.fulfillment

import com.google.research.air.cosmo.logging.CosmosTelemetry
import com.google.research.air.cosmo.logging.TelemetryEvent
import javax.inject.Inject

interface FulfillmentActionHandler {
    suspend fun execute(action: FulfillmentAction)
}

interface FulfillmentActionExecutor {
    fun supports(action: FulfillmentAction): Boolean

    suspend fun execute(action: FulfillmentAction): Boolean
}

class FulfillmentActionHandlerImpl
@Inject
constructor(
    private val actionsRepository: ActionsRepositoryContract,
    private val telemetry: CosmosTelemetry,
    private val executors: Set<@JvmSuppressWildcards FulfillmentActionExecutor>
) : FulfillmentActionHandler {
    override suspend fun execute(action: FulfillmentAction) {
        val executed = executors.firstOrNull { it.supports(action) }?.execute(action) ?: false
        telemetry.log(TelemetryEvent.ActionExecution(action.id, action.actionTypeName(), executed))
        if (executed) {
            actionsRepository.markActionAsExecuted(action.id)
        } else {
            actionsRepository.markActionAsDismissed(action.id)
        }
    }
}
