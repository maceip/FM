package com.google.research.air.cosmo.lib.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class LiteRtLmModelStorageTest {
    @Test
    fun runtimeStorageSlugMatchesDeployScriptForDefaultModel() {
        assertEquals(
            "google-gemma-4-E4B-it-litertlm-ad73b9f96e3f1965",
            liteRtLmModelStorageSlug("google-gemma-4-E4B-it-litertlm")
        )
    }

    @Test
    fun runtimeStorageSlugMatchesDeployScriptForHuggingFaceModelId() {
        assertEquals(
            "litert-community-gemma-4-E4B-it-litert-lm-27e2ad3408b7b32b",
            liteRtLmModelStorageSlug("litert-community/gemma-4-E4B-it-litert-lm")
        )
    }

    @Test
    fun runtimeStorageSlugMatchesDeployScriptForBlankModelId() {
        assertEquals(
            "default-litertlm-e3b0c44298fc1c14",
            liteRtLmModelStorageSlug("")
        )
    }
}
