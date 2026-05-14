package com.google.research.air.cosmo.di

import android.app.AlarmManager
import android.content.Context
import android.os.BatteryManager
import com.google.research.air.cosmo.runtime.CosmoProcessInitializerRunner
import com.google.research.air.cosmo.runtime.ProcessInitializerRunner
import com.google.research.air.cosmo.security.AndroidKeyStoreDatabasePassphraseProvider
import com.google.research.air.cosmo.security.DatabasePassphraseProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class BackgroundExecutor

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class UiExecutor

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds abstract fun bindInitializer(impl: CosmoProcessInitializerRunner): ProcessInitializerRunner

    @Binds abstract fun bindDatabasePassphraseProvider(impl: AndroidKeyStoreDatabasePassphraseProvider): DatabasePassphraseProvider
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    @BackgroundExecutor
    fun provideBackgroundExecutor(): Executor = Executors.newFixedThreadPool(4)

    @Provides
    @UiExecutor
    fun provideUiExecutor(): Executor = Executor { command -> android.os.Handler(android.os.Looper.getMainLooper()).post(command) }

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    fun provideAlarmManager(
        @ApplicationContext context: Context
    ): AlarmManager = context.getSystemService(AlarmManager::class.java)

    @Provides
    fun provideBatteryManager(
        @ApplicationContext context: Context
    ): BatteryManager = context.getSystemService(BatteryManager::class.java)
}
