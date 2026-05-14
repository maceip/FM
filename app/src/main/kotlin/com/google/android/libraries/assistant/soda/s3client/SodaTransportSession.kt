package com.google.android.libraries.assistant.soda.s3client

import com.google.android.libraries.assistant.soda.SodaJniLibraryLoader

open class SodaTransportSession {
    init {
        SodaJniLibraryLoader.ensureLoaded()
    }

    external fun nativeHandleResponse(
        handle: Long,
        payload: ByteArray
    )

    external fun nativeHandleError(
        handle: Long,
        message: String
    )

    external fun nativeHandleState(
        handle: Long,
        state: Int
    )
}
