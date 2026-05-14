package com.google.research.air.cosmo.lib.llm

import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AICoreLLMTest {
    @Test
    fun edgeWrapperFailsCleanlyWhenAICorePackageIsMissing() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val wrapper = EdgeAICoreClientWrapper(context, Executor { it.run() })

        assertFalse(wrapper.isAvailable())
        val exception =
            expectLlmUnavailable {
                wrapper.generate("hello") { fail("No token should stream without AICore") }
            }

        assertEquals(LlmFailureCause.AICORE_NOT_INSTALLED, exception.reason)
        assertEquals("AICore not installed", exception.message)
    }

    @Test
    fun edgeWrapperRecognizesCurrentAICorePackageName() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = EdgeAICoreClientWrapper.AICORE_PACKAGES.first()
            }
        )
        val wrapper = EdgeAICoreClientWrapper(context, Executor { it.run() })

        assertTrue(wrapper.isAvailable())
    }

    @Test
    fun aicoreLlmStreamsThroughTypedClientWrapper() = runTest {
        val llm =
            AICoreLLM(
                object : AICoreClientWrapper {
                    override fun isAvailable(): Boolean = true

                    override suspend fun generate(
                        prompt: String,
                        onToken: (String) -> Unit
                    ): String {
                        assertEquals("hello", prompt)
                        onToken("hel")
                        onToken("lo")
                        return "hello"
                    }
                }
            )
        val tokens = mutableListOf<String>()

        val text = llm.inference("hello") { tokens += it }

        assertEquals("hello", text)
        assertEquals(listOf("hel", "lo"), tokens)
    }

    @Test
    fun aicoreLlmRejectsMediaInsteadOfConvertingItToText() = runTest {
        val llm =
            AICoreLLM(
                object : AICoreClientWrapper {
                    override fun isAvailable(): Boolean = true

                    override suspend fun generate(
                        prompt: String,
                        onToken: (String) -> Unit
                    ): String = error("AICore should not receive media requests as synthetic text")
                }
            )

        val exception =
            expectLlmUnavailable {
                llm.inference(
                    listOf(
                        PromptPart.Text("describe this"),
                        PromptPart.Image(byteArrayOf(1, 2, 3), "image/jpeg")
                    )
                ) { fail("No token should stream for an unsupported request") }
            }

        assertEquals(LlmFailureCause.MODEL_NOT_READY, exception.reason)
        assertTrue(exception.message.orEmpty().contains("does not support"))
    }

    private suspend fun expectLlmUnavailable(block: suspend () -> Unit): LlmUnavailableException = try {
        block()
        fail("Expected LlmUnavailableException")
        throw AssertionError("unreachable")
    } catch (e: LlmUnavailableException) {
        e
    }

}
