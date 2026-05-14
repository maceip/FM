package com.google.android.libraries.assistant.soda.jdq

import com.google.android.libraries.assistant.soda.SodaJniLibraryLoader

class PhoneticDistance {
    init {
        SodaJniLibraryLoader.ensureLoaded()
    }

    external fun nativeCreate(): Long

    external fun nativeInit(handle: Long)

    external fun nativeUpdate(handle: Long)

    external fun nativeLookup(
        handle: Long,
        query: String
    ): ByteArray

    external fun nativeDelete(handle: Long)
}
