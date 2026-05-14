package com.google.research.air.cosmo.gatekeeping.api

enum class ContextSource {
    UNKNOWN,
    AMBIENT_AUDIO,
    BUDS_AUDIO,
    DEVICE_AUDIO,
    WATCH_AUDIO,
    NOTIFICATION,
    SCREEN,
    LOCATION,
    USER_QUERY
}
