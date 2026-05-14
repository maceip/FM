package com.google.research.air.cosmo.lib.llm

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RemoteInferenceClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stopServer() {
        server.close()
    }

    @Test
    fun inferPostsMultipartPromptAndParsesJsonResponse() = runTest {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"candidates":[{"content":{"parts":[{"text":"hello "},{"text":"world"}]}}]}""")
                .build()
        )
        val tokens = mutableListOf<String>()

        val text =
            client().infer(
                listOf(
                    PromptPart.Text("summarize"),
                    PromptPart.Audio(byteArrayOf(1, 2), "audio/wav"),
                    PromptPart.Image(byteArrayOf(3), "image/jpeg")
                )
            ) { tokens += it }

        assertEquals("hello world", text)
        assertEquals(listOf("hello world"), tokens)
        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("/v1/inference", request.url.encodedPath)
        assertEquals("PrivateToken token=\"test-token\"", request.headers["Authorization"])
        assertEquals("test-key", request.headers["X-Cosmo-Signature-Key-Id"])
        assertEquals("test-signature", request.headers["X-Cosmo-Signature"])
        assertEquals(FIXED_CLOCK.millis().toString(), request.headers["X-Cosmo-Timestamp-Millis"])

        val requestBody = requireNotNull(request.body).utf8()
        val parts =
            Json
                .parseToJsonElement(requestBody)
                .jsonObject
                .getValue("content")
                .jsonObject
                .getValue("parts")
                .jsonArray
        assertEquals(bodyHash(requestBody), request.headers["X-Cosmo-Body-SHA256"])
        assertEquals(
            "summarize",
            parts[0]
                .jsonObject
                .getValue("text")
                .jsonPrimitive.content
        )
        assertEquals(
            "audio/wav",
            parts[1]
                .jsonObject
                .getValue("blob")
                .jsonObject
                .getValue("mime_type")
                .jsonPrimitive.content
        )
        assertEquals(
            "AQI=",
            parts[1]
                .jsonObject
                .getValue("blob")
                .jsonObject
                .getValue("data")
                .jsonPrimitive.content
        )
        assertEquals(
            "image/jpeg",
            parts[2]
                .jsonObject
                .getValue("blob")
                .jsonObject
                .getValue("mime_type")
                .jsonPrimitive.content
        )
        assertEquals(
            "Aw==",
            parts[2]
                .jsonObject
                .getValue("blob")
                .jsonObject
                .getValue("data")
                .jsonPrimitive.content
        )
    }

    @Test
    fun inferParsesSseTokens() = runTest {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "text/event-stream")
                .body(
                    """
                        data: {"text":"hel"}

                        data: {"text":"lo"}

                        data: [DONE]

                    """.trimIndent()
                ).build()
        )
        val tokens = mutableListOf<String>()

        val text = client().infer(listOf(PromptPart.Text("hello"))) { tokens += it }

        assertEquals("hello", text)
        assertEquals(listOf("hel", "lo"), tokens)
    }

    @Test
    fun inferRetriesTransientFailures() = runTest {
        server.enqueue(
            MockResponse
                .Builder()
                .code(503)
                .body("busy")
                .build()
        )
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .body("""{"text":"ok"}""")
                .build()
        )

        val text = client().infer(listOf(PromptPart.Text("hello"))) {}

        assertEquals("ok", text)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun inferUsesLatestRuntimeConfigAfterClientIsCreated() = runTest {
        val secondServer = MockWebServer()
        secondServer.start()
        try {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("""{"text":"first"}""")
                    .build()
            )
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("""{"text":"stale"}""")
                    .build()
            )
            secondServer.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("""{"text":"second"}""")
                    .build()
            )
            val configSupplier = MutableConfigSupplier(config())
            val client =
                OkHttpRemoteInferenceClient(
                    StaticCredentialProvider(),
                    RemoteHttpClientFactory(TestRequestSigner(), FIXED_CLOCK),
                    configSupplier.asLambda()
                )

            assertEquals("first", client.infer(listOf(PromptPart.Text("first"))) {})
            configSupplier.current = configFor(secondServer)

            assertEquals("second", client.infer(listOf(PromptPart.Text("second"))) {})
            assertEquals(1, server.requestCount)
            assertEquals(1, secondServer.requestCount)
        } finally {
            secondServer.close()
        }
    }

    @Test
    fun inferMapsCredentialProviderFailureBeforeNetworkCall() = runTest {
        val exception =
            expectLlmUnavailable {
                client(
                    credentialProvider = FailingCredentialProvider()
                ).infer(listOf(PromptPart.Text("hello"))) {}
            }

        assertEquals(LlmFailureCause.AUTH, exception.reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun issuerClientAcquiresAnonymousCredential() = runTest {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"authorization_header":"PrivateToken token=\"issued-token\"","expires_at_epoch_millis":1770000000000}""")
                .build()
        )

        val credential =
            OkHttpAnonymousCredentialIssuerClient(
                TestRequestSigner(),
                RemoteHttpClientFactory(TestRequestSigner(), FIXED_CLOCK),
                configSupplier()
            ).issueCredential()

        assertEquals("PrivateToken token=\"issued-token\"", credential.authorizationHeader)
        assertEquals(1770000000000L, credential.expiresAtEpochMillis)

        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("/v1/private-token/issue", request.url.encodedPath)
        assertEquals("test-key", request.headers["X-Cosmo-Signature-Key-Id"])
        val body = Json.parseToJsonElement(requireNotNull(request.body).utf8()).jsonObject
        assertEquals("cosmo-remote-inference", body.getValue("audience").jsonPrimitive.content)
        assertEquals("genesis-android", body.getValue("client").jsonPrimitive.content)
        assertEquals("test-public-key", body.getValue("request_signing_public_key_spki").jsonPrimitive.content)
        assertNotNull(body["nonce"])
    }

    @Test
    fun issuerClientMapsMalformedCredentialResponseToAuthFailure() = runTest {
        server.enqueue(
            MockResponse
                .Builder()
                .code(200)
                .setHeader("Content-Type", "application/json")
                .body("""{"authorization_header":"","expires_at_epoch_millis":0}""")
                .build()
        )

        val exception =
            expectLlmUnavailable {
                OkHttpAnonymousCredentialIssuerClient(
                    TestRequestSigner(),
                    RemoteHttpClientFactory(TestRequestSigner(), FIXED_CLOCK),
                    configSupplier()
                ).issueCredential()
            }

        assertEquals(LlmFailureCause.AUTH, exception.reason)
    }

    @Test
    fun refreshingCredentialProviderUsesUnexpiredStoredCredential() = runTest {
        val supplier = configSupplier()
        val config = supplier()
        val store =
            InMemoryRemoteCredentialStore(
                StoredAnonymousCredential(
                    authorizationHeader = "PrivateToken token=\"cached-token\"",
                    expiresAtEpochMillis = FUTURE_EXPIRY_MILLIS,
                    baseUrl = config.baseUrl,
                    audience = config.audience
                )
            )
        val issuer = CountingCredentialIssuer()
        val provider =
            RefreshingAnonymousCredentialProvider(
                store = store,
                issuerClient = issuer,
                clock = FIXED_CLOCK,
                configSupplier = supplier
            )

        val credential = provider.credential()

        assertEquals("PrivateToken token=\"cached-token\"", credential.authorizationHeader)
        assertEquals(0, issuer.count)
    }

    @Test
    fun refreshingCredentialProviderRefreshesExpiredStoredCredential() = runTest {
        val supplier = configSupplier()
        val config = supplier()
        val store =
            InMemoryRemoteCredentialStore(
                StoredAnonymousCredential(
                    authorizationHeader = "PrivateToken token=\"expired-token\"",
                    expiresAtEpochMillis = FIXED_CLOCK.millis(),
                    baseUrl = config.baseUrl,
                    audience = config.audience
                )
            )
        val issuer = CountingCredentialIssuer()
        val provider =
            RefreshingAnonymousCredentialProvider(
                store = store,
                issuerClient = issuer,
                clock = FIXED_CLOCK,
                configSupplier = supplier
            )

        val credential = provider.credential()

        assertEquals("PrivateToken token=\"issued-token\"", credential.authorizationHeader)
        assertEquals(1, issuer.count)
        assertEquals("PrivateToken token=\"issued-token\"", store.read()?.authorizationHeader)
    }

    @Test
    fun refreshingCredentialProviderRefreshesStoredCredentialFromDifferentScope() = runTest {
        val supplier = configSupplier()
        val config = supplier()
        val store =
            InMemoryRemoteCredentialStore(
                StoredAnonymousCredential(
                    authorizationHeader = "PrivateToken token=\"cached-token\"",
                    expiresAtEpochMillis = FUTURE_EXPIRY_MILLIS,
                    baseUrl = "https://old.example.com/",
                    audience = "old-audience"
                )
            )
        val issuer = CountingCredentialIssuer()
        val provider =
            RefreshingAnonymousCredentialProvider(
                store = store,
                issuerClient = issuer,
                clock = FIXED_CLOCK,
                configSupplier = supplier
            )

        val credential = provider.credential()

        assertEquals("PrivateToken token=\"issued-token\"", credential.authorizationHeader)
        assertEquals(1, issuer.count)
        assertEquals(config.baseUrl, store.read()?.baseUrl)
        assertEquals(config.audience, store.read()?.audience)
    }

    @Test
    fun inferMapsHttpAuthFailure() = runTest {
        server.enqueue(
            MockResponse
                .Builder()
                .code(401)
                .body("unauthorized")
                .build()
        )

        val exception =
            expectLlmUnavailable {
                client().infer(listOf(PromptPart.Text("hello"))) {}
            }

        assertEquals(LlmFailureCause.AUTH, exception.reason)
        assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
    }

    @Test
    fun inferMapsRateLimitToQuotaWithoutRetrying() = runTest {
        server.enqueue(
            MockResponse
                .Builder()
                .code(429)
                .body("quota")
                .build()
        )

        val exception =
            expectLlmUnavailable {
                client().infer(listOf(PromptPart.Text("hello"))) {}
            }

        assertEquals(LlmFailureCause.QUOTA, exception.reason)
        assertEquals(1, server.requestCount)
    }

    private fun client(
        credentialProvider: AnonymousCredentialProvider = StaticCredentialProvider()
    ): OkHttpRemoteInferenceClient {
        val supplier = configSupplier()
        return OkHttpRemoteInferenceClient(
            credentialProvider,
            RemoteHttpClientFactory(TestRequestSigner(), FIXED_CLOCK),
            supplier
        )
    }

    private fun configSupplier(): () -> RemoteInferenceConfig = { config() }

    private fun config(): RemoteInferenceConfig = configFor(server)

    private fun configFor(targetServer: MockWebServer): RemoteInferenceConfig = RemoteInferenceConfig(
        baseUrl = targetServer.url("/").toString(),
        certificatePin = "",
        audience = "cosmo-remote-inference"
    )

    private suspend fun expectLlmUnavailable(block: suspend () -> Unit): LlmUnavailableException = try {
        block()
        fail("Expected LlmUnavailableException")
        throw AssertionError("unreachable")
    } catch (e: LlmUnavailableException) {
        e
    }

    private class StaticCredentialProvider : AnonymousCredentialProvider {
        override suspend fun credential(): AnonymousRateLimitedCredential = AnonymousRateLimitedCredential(
            authorizationHeader = "PrivateToken token=\"test-token\"",
            expiresAtEpochMillis = 1770000000000L
        )
    }

    /**
     * Mutable supplier used in place of the deleted [RemoteInferenceConfigProvider]. Tests pin
     * the initial config and may swap it mid-run to exercise re-read behavior on long-lived
     * clients (see `inferUsesLatestRuntimeConfigAfterClientIsCreated`).
     */
    private class MutableConfigSupplier(initial: RemoteInferenceConfig) {
        @Volatile var current: RemoteInferenceConfig = initial
        fun asLambda(): () -> RemoteInferenceConfig = { current }
    }

    private class FailingCredentialProvider : AnonymousCredentialProvider {
        override suspend fun credential(): AnonymousRateLimitedCredential = throw LlmUnavailableException(LlmFailureCause.AUTH, "no credential")
    }

    private class TestRequestSigner : RequestSigner {
        override val keyId: String = "test-key"
        override val publicKeySpkiBase64Url: String = "test-public-key"

        override fun sign(input: RequestSigningInput): String = "test-signature"
    }

    private class InMemoryRemoteCredentialStore(
        private var credential: StoredAnonymousCredential? = null
    ) : RemoteCredentialStore {
        override suspend fun read(): StoredAnonymousCredential? = credential

        override suspend fun write(credential: StoredAnonymousCredential) {
            this.credential = credential
        }

        override suspend fun clear() {
            credential = null
        }
    }

    private class CountingCredentialIssuer : AnonymousCredentialIssuerClient {
        var count: Int = 0

        override suspend fun issueCredential(): AnonymousRateLimitedCredential {
            count += 1
            return AnonymousRateLimitedCredential(
                authorizationHeader = "PrivateToken token=\"issued-token\"",
                expiresAtEpochMillis = FUTURE_EXPIRY_MILLIS
            )
        }
    }

    private fun bodyHash(body: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8))
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(digest)
    }

    private companion object {
        val FIXED_CLOCK: Clock = Clock.fixed(Instant.parse("2026-05-08T00:00:00Z"), ZoneOffset.UTC)
        const val FUTURE_EXPIRY_MILLIS = 1_900_000_000_000L
    }
}
