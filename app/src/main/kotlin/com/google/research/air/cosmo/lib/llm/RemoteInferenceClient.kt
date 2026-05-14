package com.google.research.air.cosmo.lib.llm

import java.io.IOException
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.CertificatePinner
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

interface RemoteInferenceClient {
    suspend fun infer(
        parts: List<PromptPart>,
        onToken: (String) -> Unit
    ): String
}

data class RemoteInferenceConfig(
    val baseUrl: String,
    val certificatePin: String,
    val audience: String
) {
    fun requireValid(): HttpUrl {
        val error = validationError()
        if (error != null) throw error
        return baseUrl.toHttpUrl()
    }

    private fun validationError(): LlmUnavailableException? {
        if (baseUrl.isBlank()) return LlmUnavailableException(LlmFailureCause.CONFIGURATION, "Remote inference base URL is not configured")
        val url = baseUrl.toHttpUrl()
        if (url.scheme != "https" && !url.isLoopback()) return LlmUnavailableException(LlmFailureCause.CONFIGURATION, "Remote inference requires HTTPS")
        if (url.scheme == "https" && certificatePin.isBlank()) return LlmUnavailableException(LlmFailureCause.CONFIGURATION, "Remote inference certificate pin is not configured")
        if (audience.isBlank()) return LlmUnavailableException(LlmFailureCause.CONFIGURATION, "Remote inference audience is not configured")
        return null
    }

    companion object {
        /**
         * Reads the build-time `cosmo.remoteInference.*` Gradle properties exposed as
         * `CosmoBuildConfig` fields. Replaces the deleted [RemoteInferenceConfigProvider] +
         * encrypted-DataStore runtime override layer per architecture-delta C4 row 7 — if a
         * runtime override is ever needed again, reintroduce it as a single new field on this
         * companion rather than as a separate Provider/Store/Source/DataStore stack.
         */
        fun fromBuildConfig(): RemoteInferenceConfig = RemoteInferenceConfig(
            baseUrl = com.google.research.air.cosmo.runtime.CosmoBuildConfig.remoteInferenceBaseUrl,
            certificatePin = com.google.research.air.cosmo.runtime.CosmoBuildConfig.remoteInferenceCertificatePin,
            audience = com.google.research.air.cosmo.runtime.CosmoBuildConfig.remoteInferenceAudience
        )
    }
}

data class AnonymousRateLimitedCredential(
    val authorizationHeader: String,
    val expiresAtEpochMillis: Long
) {
    init {
        require(authorizationHeader.startsWith(PRIVATE_TOKEN_SCHEME)) { "authorizationHeader must use the PrivateToken scheme" }
    }

    companion object {
        private const val PRIVATE_TOKEN_SCHEME = "PrivateToken "
    }
}

interface AnonymousCredentialProvider {
    suspend fun credential(): AnonymousRateLimitedCredential
}

interface AnonymousCredentialIssuerClient {
    suspend fun issueCredential(): AnonymousRateLimitedCredential
}

private const val REMOTE_CREDENTIAL_REFRESH_SKEW_MILLIS = 5 * 60 * 1000L

@Singleton
class RefreshingAnonymousCredentialProvider internal constructor(
    private val store: RemoteCredentialStore,
    private val issuerClient: AnonymousCredentialIssuerClient,
    private val clock: java.time.Clock,
    // Test seam: production uses `RemoteInferenceConfig.fromBuildConfig`; tests inject a mutable
    // supplier. Replaces the deleted [RemoteInferenceConfigProvider] indirection.
    private val configSupplier: () -> RemoteInferenceConfig
) : AnonymousCredentialProvider {
    @Inject constructor(
        store: RemoteCredentialStore,
        issuerClient: AnonymousCredentialIssuerClient,
        clock: java.time.Clock
    ) : this(store, issuerClient, clock, RemoteInferenceConfig.Companion::fromBuildConfig)

    private val mutex = kotlinx.coroutines.sync.Mutex()

    override suspend fun credential(): AnonymousRateLimitedCredential = mutex.lockAndRefresh()

    private suspend fun kotlinx.coroutines.sync.Mutex.lockAndRefresh(): AnonymousRateLimitedCredential {
        lock()
        try {
            val config = configSupplier()
            store.read()?.let { stored ->
                if (stored.isUsableFor(config, clock.millis())) {
                    return stored.toCredential()
                }
            }
            val issued = issuerClient.issueCredential()
            store.write(
                StoredAnonymousCredential(
                    authorizationHeader = issued.authorizationHeader,
                    expiresAtEpochMillis = issued.expiresAtEpochMillis,
                    baseUrl = config.baseUrl,
                    audience = config.audience
                )
            )
            return issued
        } finally {
            unlock()
        }
    }

}

@Singleton
class OkHttpAnonymousCredentialIssuerClient internal constructor(
    private val requestSigner: RequestSigner,
    private val httpClientFactory: RemoteHttpClientFactory,
    private val configSupplier: () -> RemoteInferenceConfig
) : AnonymousCredentialIssuerClient {
    @Inject constructor(
        requestSigner: RequestSigner,
        httpClientFactory: RemoteHttpClientFactory
    ) : this(requestSigner, httpClientFactory, RemoteInferenceConfig.Companion::fromBuildConfig)

    override suspend fun issueCredential(): AnonymousRateLimitedCredential = withContext(Dispatchers.IO) {
        val config = configSupplier()
        val httpClient = httpClientFactory.create(config)
        val url =
            config
                .requireValid()
                .newBuilder()
                .addPathSegment("v1")
                .addPathSegment("private-token")
                .addPathSegment("issue")
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .post(buildIssueRequest(config).toString().toRequestBody(JSON))
                .header("Accept", "application/json")
                .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw LlmUnavailableException(response.failureCause(), "Anonymous credential issue failed with HTTP ${response.code}")
                }
                parseCredentialIssueResponse(response.body.string())
            }
        } catch (e: LlmUnavailableException) {
            throw e
        } catch (e: javax.net.ssl.SSLPeerUnverifiedException) {
            throw LlmUnavailableException(LlmFailureCause.BAD_CERTIFICATE, "Anonymous credential certificate pinning failed", e)
        } catch (e: SocketTimeoutException) {
            throw LlmUnavailableException(LlmFailureCause.TIMEOUT, "Anonymous credential request timed out", e)
        } catch (e: IOException) {
            throw LlmUnavailableException(LlmFailureCause.NETWORK, "Anonymous credential network request failed", e)
        }
    }

    private fun buildIssueRequest(config: RemoteInferenceConfig): JsonObject = buildJsonObject {
        put("audience", config.audience)
        put("client", CLIENT_NAME)
        put("nonce", newNonce())
        put("request_signing_public_key_spki", requestSigner.publicKeySpkiBase64Url)
    }

    private fun parseCredentialIssueResponse(body: String): AnonymousRateLimitedCredential {
        val root = json.parseToJsonElement(body).jsonObject
        val authorizationHeader = root["authorization_header"].asString()
        val expiresAtEpochMillis = root["expires_at_epoch_millis"]?.jsonPrimitive?.longOrNull ?: 0L
        if (authorizationHeader.isBlank()) throw LlmUnavailableException(LlmFailureCause.AUTH, "Anonymous credential issuer omitted authorization_header")
        if (expiresAtEpochMillis <= 0L) throw LlmUnavailableException(LlmFailureCause.AUTH, "Anonymous credential issuer omitted expires_at_epoch_millis")
        return AnonymousRateLimitedCredential(authorizationHeader, expiresAtEpochMillis)
    }

    private fun newNonce(): String {
        val nonce = ByteArray(NONCE_BYTES)
        secureRandom.nextBytes(nonce)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce)
    }

    private companion object {
        const val CLIENT_NAME = "genesis-android"
        const val NONCE_BYTES = 16
        val JSON = "application/json".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
        val secureRandom = SecureRandom()
    }
}

@Singleton
class RemoteHttpClientFactory
@Inject
constructor(
    private val requestSigner: RequestSigner,
    private val clock: java.time.Clock
) {
    @Volatile
    private var cachedClient: CachedRemoteClient? = null

    fun create(config: RemoteInferenceConfig = RemoteInferenceConfig.fromBuildConfig()): OkHttpClient {
        cachedClient?.takeIf { it.config == config }?.let { return it.client }
        return synchronized(this) {
            cachedClient?.takeIf { it.config == config }?.client
                ?: buildClient(config).also { client ->
                    cachedClient = CachedRemoteClient(config, client)
                }
        }
    }

    private fun buildClient(config: RemoteInferenceConfig): OkHttpClient {
        val url = config.requireValid()
        return OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .certificatePinner(
                CertificatePinner
                    .Builder()
                    .apply {
                        if (config.certificatePin.isNotBlank()) add(url.host, config.certificatePin)
                    }.build()
            ).addInterceptor(RequestSigningInterceptor(requestSigner, clock))
            .addInterceptor(RetryInterceptor(maxRetries = 3))
            .build()
    }

    private data class CachedRemoteClient(
        val config: RemoteInferenceConfig,
        val client: OkHttpClient
    )
}

@Singleton
class OkHttpRemoteInferenceClient internal constructor(
    private val credentialProvider: AnonymousCredentialProvider,
    private val httpClientFactory: RemoteHttpClientFactory,
    private val configSupplier: () -> RemoteInferenceConfig
) : RemoteInferenceClient {
    @Inject constructor(
        credentialProvider: AnonymousCredentialProvider,
        httpClientFactory: RemoteHttpClientFactory
    ) : this(credentialProvider, httpClientFactory, RemoteInferenceConfig.Companion::fromBuildConfig)

    override suspend fun infer(
        parts: List<PromptPart>,
        onToken: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val config = configSupplier()
        val httpClient = httpClientFactory.create(config)
        val url =
            config
                .requireValid()
                .newBuilder()
                .addPathSegment("v1")
                .addPathSegment("inference")
                .build()
        val credential = credentialProvider.credential()
        val request =
            Request
                .Builder()
                .url(url)
                .post(buildRequest(parts).toString().toRequestBody(JSON))
                .header("Accept", "text/event-stream, application/json")
                .header("Authorization", credential.authorizationHeader)
                .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw LlmUnavailableException(response.failureCause(), "Remote inference failed with HTTP ${response.code}")
                }
                if (response.header("Content-Type").orEmpty().contains("text/event-stream")) {
                    parseSse(response, onToken)
                } else {
                    parseJson(response.body.string(), onToken)
                }
            }
        } catch (e: LlmUnavailableException) {
            throw e
        } catch (e: javax.net.ssl.SSLPeerUnverifiedException) {
            throw LlmUnavailableException(LlmFailureCause.BAD_CERTIFICATE, "Remote inference certificate pinning failed", e)
        } catch (e: SocketTimeoutException) {
            throw LlmUnavailableException(LlmFailureCause.TIMEOUT, "Remote inference request timed out", e)
        } catch (e: IOException) {
            throw LlmUnavailableException(LlmFailureCause.NETWORK, "Remote inference network request failed", e)
        }
    }

    private fun buildRequest(parts: List<PromptPart>): JsonObject = buildJsonObject {
        put(
            "content",
            buildJsonObject {
                put(
                    "parts",
                    buildJsonArray {
                        parts.forEach { add(it.toJson()) }
                    }
                )
            }
        )
    }

    private fun PromptPart.toJson(): JsonObject = when (this) {
        is PromptPart.Text -> buildJsonObject { put("text", text) }
        is PromptPart.Audio -> blobJson(mimeType, Base64.getEncoder().encodeToString(audioBytes))
        is PromptPart.Image -> blobJson(mimeType, Base64.getEncoder().encodeToString(imageBytes))
    }

    private fun blobJson(
        mimeType: String,
        data: String
    ): JsonObject = buildJsonObject {
        put(
            "blob",
            buildJsonObject {
                put("mime_type", mimeType)
                put("data", data)
            }
        )
    }

    private fun parseSse(
        response: Response,
        onToken: (String) -> Unit
    ): String {
        val out = StringBuilder()
        response.body.source().use { source ->
            var done = false
            while (!source.exhausted() && !done) {
                val line = source.readUtf8Line().orEmpty()
                val payload = line.takeIf { it.startsWith("data:") }?.removePrefix("data:")?.trim()
                done = payload?.let { handleSsePayload(it, out, onToken) } == true
            }
        }
        return out.toString()
    }

    private fun handleSsePayload(
        payload: String,
        out: StringBuilder,
        onToken: (String) -> Unit
    ): Boolean {
        if (payload == "[DONE]") return true
        val token =
            json
                .parseToJsonElement(payload)
                .asObject()
                ?.get("text")
                .asString()
        if (token.isNotEmpty()) {
            out.append(token)
            onToken(token)
        }
        return false
    }

    private fun parseJson(
        body: String,
        onToken: (String) -> Unit
    ): String {
        val root = json.parseToJsonElement(body).jsonObject
        val text =
            root["candidates"]
                .asArray()
                ?.firstOrNull()
                .asObject()
                ?.get("content")
                .asObject()
                ?.get("parts")
                .asArray()
                ?.joinToString("") { it.asObject()?.get("text").asString() }
                ?: root["text"].asString()
        if (text.isNotEmpty()) onToken(text)
        return text
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
    }
}

private fun HttpUrl.isLoopback(): Boolean = host == "localhost" || host == "127.0.0.1" || host == "::1"

private fun StoredAnonymousCredential.toCredential(): AnonymousRateLimitedCredential = AnonymousRateLimitedCredential(authorizationHeader, expiresAtEpochMillis)

private fun StoredAnonymousCredential.isUsableFor(
    config: RemoteInferenceConfig,
    nowEpochMillis: Long
): Boolean =
    baseUrl == config.baseUrl &&
        audience == config.audience &&
        expiresAtEpochMillis - nowEpochMillis > REMOTE_CREDENTIAL_REFRESH_SKEW_MILLIS

private fun Response.failureCause(): LlmFailureCause = when (code) {
    401, 403 -> LlmFailureCause.AUTH
    408 -> LlmFailureCause.TIMEOUT
    429 -> LlmFailureCause.QUOTA
    else -> LlmFailureCause.NETWORK
}

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

private fun JsonElement?.asString(): String = (this as? JsonPrimitive)?.contentOrNull.orEmpty()

private class RetryInterceptor(
    private val maxRetries: Int
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var last: IOException? = null
        repeat(maxRetries) { attempt ->
            try {
                val response = chain.proceed(chain.request())
                if (response.code !in retryableCodes || attempt == maxRetries - 1) return response
                response.close()
            } catch (e: SocketTimeoutException) {
                last = e
                if (attempt == maxRetries - 1) throw LlmUnavailableException(LlmFailureCause.TIMEOUT, "Remote inference request timed out", e)
            } catch (e: javax.net.ssl.SSLPeerUnverifiedException) {
                throw LlmUnavailableException(LlmFailureCause.BAD_CERTIFICATE, "Remote inference certificate pinning failed", e)
            } catch (e: IOException) {
                last = e
                if (attempt == maxRetries - 1) throw e
            }
            Thread.sleep(100L * (1L shl attempt))
        }
        throw last ?: IOException("Retry exhausted")
    }

    companion object {
        private val retryableCodes = setOf(408, 500, 502, 503, 504)
    }
}
