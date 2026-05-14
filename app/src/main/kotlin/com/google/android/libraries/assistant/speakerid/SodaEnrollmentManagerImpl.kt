package com.google.android.libraries.assistant.speakerid

import com.google.android.libraries.assistant.soda.SodaJniLibraryLoader

class SodaEnrollmentManagerImpl {
    init {
        SodaJniLibraryLoader.ensureLoaded()
    }

    external fun nativeConstruct(): Long

    external fun nativeDelete(handle: Long)

    external fun nativeProcessRequest(
        handle: Long,
        payload: ByteArray
    )

    external fun nativeGetSpeakerInfo(handle: Long): ByteArray

    external fun nativeGetVoiceMatchModels(handle: Long): ByteArray

    external fun nativeGetEnrollmentAudio(handle: Long): ByteArray

    external fun nativeGetEnrollmentAudioInfo(handle: Long): ByteArray
}
