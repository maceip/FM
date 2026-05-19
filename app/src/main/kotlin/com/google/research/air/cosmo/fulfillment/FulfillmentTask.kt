package com.google.research.air.cosmo.fulfillment

import com.google.research.air.cosmo.gatekeeping.GatekeepingResult

/**
 * Recovered cosmo `FulfillmentTask` queued by [FulfillmentOrchestratorImpl].
 */
internal data class FulfillmentTask(
    val triggerType: FulfillmentTriggerType,
    val userQuery: String = "",
    val gatekeeperResultString: String = "",
    val conversationHistory: String = "",
    val gatekeepingResult: GatekeepingResult? = null,
)
