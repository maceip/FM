package com.google.research.air.cosmo.fulfillment

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FulfillmentModule {
    @Binds @Singleton abstract fun bindActionsRepository(impl: ActionsRepository): ActionsRepositoryContract

    @Binds abstract fun bindActionHandler(impl: FulfillmentActionHandlerImpl): FulfillmentActionHandler

    @Binds abstract fun bindOrchestrator(impl: FulfillmentOrchestratorImpl): FulfillmentOrchestrator

    @Binds @IntoSet abstract fun bindIntentActionExecutor(impl: IntentFulfillmentActionExecutor): FulfillmentActionExecutor

    @Binds @IntoSet abstract fun bindMemoryActionExecutor(impl: MemoryFulfillmentActionExecutor): FulfillmentActionExecutor

    @Binds @IntoSet abstract fun bindTaskMonitorActionExecutor(impl: TaskMonitorFulfillmentActionExecutor): FulfillmentActionExecutor

    @Binds @IntoSet abstract fun bindPassiveActionExecutor(impl: PassiveFulfillmentActionExecutor): FulfillmentActionExecutor
}
