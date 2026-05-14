package com.google.android.libraries.assistant.soda

internal object SodaJniLibraryLoader {
    @Volatile private var loaded = false

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        System.loadLibrary("soda_dev_jni")
        loaded = true
    }
}
