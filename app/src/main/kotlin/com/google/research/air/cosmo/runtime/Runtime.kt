package com.google.research.air.cosmo.runtime

import android.app.Application
import android.content.Context
import com.google.research.air.cosmo.batch.BatchWorkScheduler
import com.google.research.air.cosmo.setting.CosmoStateManager
import javax.inject.Inject

object PureProcess {
    fun isPure(context: Context): Boolean {
        val name = Application.getProcessName()
        return name != context.packageName && name.endsWith(":primes_lifeboat")
    }
}

interface ProcessInitializerRunner {
    fun init()
}

class CosmoProcessInitializerRunner
@Inject
constructor(
    private val batchWorkScheduler: BatchWorkScheduler,
    private val cosmoStateManager: CosmoStateManager
) : ProcessInitializerRunner {
    override fun init() {
        cosmoStateManager.updateAndScheduleCosmoState()
        batchWorkScheduler.schedule()
    }
}
