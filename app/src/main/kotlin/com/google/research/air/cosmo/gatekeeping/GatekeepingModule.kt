package com.google.research.air.cosmo.gatekeeping

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.SharedFlow

@Module
@InstallIn(SingletonComponent::class)
abstract class GatekeeperBindings {
    @Binds @RegexGatekeeper
    abstract fun bindRegex(impl: RegexGatekeepingModelV0): GatekeepingModel

    @Binds @BertGatekeeper
    abstract fun bindBert(impl: BertClassifierGatekeepingModel): GatekeepingModel

    @Binds abstract fun bindCombined(impl: CombinedBertRegexGatekeepingModel): GatekeepingModel

    @Binds abstract fun bindLogger(impl: GatekeeperLoggerImpl): GatekeeperLogger

    @Binds @Singleton abstract fun bindGatekeeperOutputSource(impl: Gatekeeper): GatekeeperOutputSource
}

@Module
@InstallIn(SingletonComponent::class)
object GatekeeperOutputModule {
    @Provides @GatekeeperOutput
    fun provideShared(source: GatekeeperOutputSource): SharedFlow<GatekeepingResult> = source.results

    @Provides @GatekeepingModelId
    fun modelId(): String = "both"
}
