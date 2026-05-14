package com.google.research.air.cosmo.fulfillment

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FulfillmentModule {
    @Binds @Singleton abstract fun bindActionsRepository(impl: ActionsRepository): ActionsRepositoryContract

    @Binds abstract fun bindActionHandler(impl: FulfillmentActionHandlerImpl): FulfillmentActionHandler

    @Binds abstract fun bindOrchestrator(impl: FulfillmentOrchestratorImpl): FulfillmentOrchestrator
}
