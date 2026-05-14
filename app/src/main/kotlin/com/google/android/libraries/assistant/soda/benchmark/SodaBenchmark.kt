package com.google.android.libraries.assistant.soda.benchmark

import com.google.android.libraries.assistant.soda.SodaJniLibraryLoader

object SodaBenchmark {
    init {
        SodaJniLibraryLoader.ensureLoaded()
    }

    external fun nativeRunBenchmarking()
}
