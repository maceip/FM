package com.google.research.air.cosmo.gatekeeping

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class GatekeeperOutput

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class RegexGatekeeper

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class BertGatekeeper

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class GatekeepingModelId
