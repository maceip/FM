package com.google.research.air.cosmo.lib.llm

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkAwareLLMTest {
    @Test
    fun routesToLocalFirstWhenNetworkIsValidatedAndFastEnough() = runTest {
        val policyProvider =
            FakeNetworkPolicyProvider(
                NetworkPolicy(
                    available = true,
                    validated = true,
                    unmetered = false,
                    vpnActive = false,
                    downstreamKbps = 1_000,
                    upstreamKbps = 256
                )
            )
        val llm =
            NetworkAwareLLM(
                local = FixedLLM("local"),
                remote = FixedLLM("remote"),
                networkPolicyProvider = policyProvider
            )

        assertEquals("local", llm.generateText("hello"))
        assertEquals(ModelPath.LOCAL_LLM, llm.activePath.value)
        assertEquals(LLMRouteReason.LOCAL_ELIGIBLE, llm.routeDecision.value.reason)
    }

    @Test
    fun routesToLocalWhenVpnIsActive() = runTest {
        val policyProvider =
            FakeNetworkPolicyProvider(
                NetworkPolicy(
                    available = true,
                    validated = true,
                    unmetered = true,
                    vpnActive = true,
                    downstreamKbps = 10_000,
                    upstreamKbps = 10_000
                )
            )
        val llm =
            NetworkAwareLLM(
                local = FixedLLM("local"),
                remote = FixedLLM("remote"),
                networkPolicyProvider = policyProvider
            )

        assertEquals("local", llm.generateText("hello"))
        assertEquals(ModelPath.LOCAL_LLM, llm.activePath.value)
        assertEquals(LLMRouteReason.VPN_ACTIVE, llm.routeDecision.value.reason)
    }

    @Test
    fun routesToLocalWhenNetworkBandwidthIsTooLow() = runTest {
        val policyProvider =
            FakeNetworkPolicyProvider(
                NetworkPolicy(
                    available = true,
                    validated = true,
                    unmetered = true,
                    vpnActive = false,
                    downstreamKbps = 511,
                    upstreamKbps = 128
                )
            )
        val llm =
            NetworkAwareLLM(
                local = FixedLLM("local"),
                remote = FixedLLM("remote"),
                networkPolicyProvider = policyProvider
            )

        assertEquals("local", llm.generateText("hello"))
        assertEquals(ModelPath.LOCAL_LLM, llm.activePath.value)
        assertEquals(LLMRouteReason.DOWNSTREAM_TOO_LOW, llm.routeDecision.value.reason)
    }

    @Test
    fun routeDecisionUpdatesWhenNetworkPolicyChanges() = runTest {
        val policyProvider =
            MutableNetworkPolicyProvider(
                NetworkPolicy(
                    available = true,
                    validated = true,
                    unmetered = true,
                    vpnActive = false,
                    downstreamKbps = 10_000,
                    upstreamKbps = 10_000
                )
            )
        val llm =
            NetworkAwareLLM(
                local = FixedLLM("local"),
                remote = FixedLLM("remote"),
                networkPolicyProvider = policyProvider
            )

        assertEquals("local", llm.generateText("hello"))

        policyProvider.policy = NetworkPolicy.UNAVAILABLE

        assertEquals("local", llm.generateText("hello"))
        assertEquals(ModelPath.LOCAL_LLM, llm.activePath.value)
        assertEquals(LLMRouteReason.NETWORK_UNAVAILABLE, llm.routeDecision.value.reason)
    }

    @Test
    fun fallsBackToRemoteWhenLocalPathIsUnavailableAndNetworkIsEligible() = runTest {
        val policyProvider =
            FakeNetworkPolicyProvider(
                NetworkPolicy(
                    available = true,
                    validated = true,
                    unmetered = true,
                    vpnActive = false,
                    downstreamKbps = 10_000,
                    upstreamKbps = 10_000
                )
            )
        val llm =
            NetworkAwareLLM(
                local = FailingLLM(LlmFailureCause.MODEL_NOT_READY),
                remote = FixedLLM("remote"),
                networkPolicyProvider = policyProvider
            )

        assertEquals("remote", llm.generateText("hello"))
        assertEquals(ModelPath.CLOUD_LLM, llm.activePath.value)
        assertEquals(LLMRouteReason.LOCAL_UNAVAILABLE_REMOTE_ELIGIBLE, llm.routeDecision.value.reason)
    }

    @Test
    fun propagatesRemoteFailureWhenLocalPathIsUnavailable() = runTest {
        val policyProvider =
            FakeNetworkPolicyProvider(
                NetworkPolicy(
                    available = true,
                    validated = true,
                    unmetered = true,
                    vpnActive = false,
                    downstreamKbps = 10_000,
                    upstreamKbps = 10_000
                )
            )
        val llm =
            NetworkAwareLLM(
                local = FailingLLM(LlmFailureCause.MODEL_NOT_READY),
                remote = FailingLLM(LlmFailureCause.INTERNAL),
                networkPolicyProvider = policyProvider
            )

        val exception =
            try {
                llm.generateText("hello")
                throw AssertionError("Expected LlmUnavailableException")
            } catch (e: LlmUnavailableException) {
                e
            }

        assertEquals(LlmFailureCause.INTERNAL, exception.reason)
        assertEquals(ModelPath.LOCAL_LLM, llm.activePath.value)
        assertEquals(LLMRouteReason.REMOTE_UNAVAILABLE, llm.routeDecision.value.reason)
    }

    @Test
    fun propagatesLocalFailureWhenNetworkCannotUseRemote() = runTest {
        val llm =
            NetworkAwareLLM(
                local = FailingLLM(LlmFailureCause.MODEL_NOT_READY),
                remote = FixedLLM("remote"),
                networkPolicyProvider = FakeNetworkPolicyProvider(NetworkPolicy.UNAVAILABLE)
            )

        val exception =
            try {
                llm.generateText("hello")
                throw AssertionError("Expected LlmUnavailableException")
            } catch (e: LlmUnavailableException) {
                e
            }

        assertEquals(LlmFailureCause.MODEL_NOT_READY, exception.reason)
        assertEquals(ModelPath.LOCAL_LLM, llm.activePath.value)
        assertEquals(LLMRouteReason.NETWORK_UNAVAILABLE, llm.routeDecision.value.reason)
    }

    private class FakeNetworkPolicyProvider(
        private val policy: NetworkPolicy
    ) : NetworkPolicyProvider {
        override fun currentNetworkPolicy(): NetworkPolicy = policy
    }

    private class MutableNetworkPolicyProvider(
        var policy: NetworkPolicy
    ) : NetworkPolicyProvider {
        override fun currentNetworkPolicy(): NetworkPolicy = policy
    }

    private class FixedLLM(
        private val response: String
    ) : LLM {
        override suspend fun inference(
            prompt: String,
            onToken: (String) -> Unit
        ): String {
            onToken(response)
            return response
        }

        override suspend fun inference(
            parts: List<PromptPart>,
            onToken: (String) -> Unit
        ): String {
            onToken(response)
            return response
        }
    }

    private class FailingLLM(
        private val reason: LlmFailureCause
    ) : LLM {
        override suspend fun inference(
            prompt: String,
            onToken: (String) -> Unit
        ): String = throw LlmUnavailableException(reason, "failed")

        override suspend fun inference(
            parts: List<PromptPart>,
            onToken: (String) -> Unit
        ): String = throw LlmUnavailableException(reason, "failed")
    }
}
