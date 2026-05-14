package com.google.research.air.cosmo.lib.llm

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

data class RequestSigningInput(
    val method: String,
    val pathAndQuery: String,
    val timestampMillis: Long,
    val nonce: String,
    val bodySha256Base64Url: String
) {
    fun canonicalPayload(): String = listOf(
        method.uppercase(),
        pathAndQuery,
        timestampMillis.toString(),
        nonce,
        bodySha256Base64Url
    ).joinToString("\n")
}

interface RequestSigner {
    val keyId: String
    val publicKeySpkiBase64Url: String

    fun sign(input: RequestSigningInput): String
}

@Singleton
class AndroidKeyStoreRequestSigner
@Inject
constructor() : RequestSigner {
    override val keyId: String = "android-keystore:$KEY_ALIAS"
    override val publicKeySpkiBase64Url: String
        get() {
            privateKey()
            val certificate =
                KeyStore
                    .getInstance(ANDROID_KEYSTORE)
                    .apply { load(null) }
                    .getCertificate(KEY_ALIAS)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(certificate.publicKey.encoded)
        }

    override fun sign(input: RequestSigningInput): String {
        val signature =
            Signature
                .getInstance(SIGNATURE_ALGORITHM)
                .apply {
                    initSign(privateKey())
                    update(input.canonicalPayload().toByteArray(Charsets.UTF_8))
                }.sign()
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
    }

    private fun privateKey(): PrivateKey {
        val keyStore =
            KeyStore
                .getInstance(ANDROID_KEYSTORE)
                .apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
        if (existing != null) return existing.privateKey

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKeyPair().private
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "cosmo_remote_request_signing_v1"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}

class RequestSigningInterceptor(
    private val requestSigner: RequestSigner,
    private val clock: Clock
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val timestamp = clock.millis()
        val nonce = UUID.randomUUID().toString()
        val bodySha256 = request.bodySha256Base64Url()
        val signingInput =
            RequestSigningInput(
                method = request.method,
                pathAndQuery = request.pathAndQuery(),
                timestampMillis = timestamp,
                nonce = nonce,
                bodySha256Base64Url = bodySha256
            )
        val signature =
            try {
                requestSigner.sign(signingInput)
            } catch (e: Exception) {
                throw IOException("Remote request signing failed", e)
            }

        return chain.proceed(
            request
                .newBuilder()
                .header("X-Cosmo-Timestamp-Millis", timestamp.toString())
                .header("X-Cosmo-Nonce", nonce)
                .header("X-Cosmo-Body-SHA256", bodySha256)
                .header("X-Cosmo-Signature-Key-Id", requestSigner.keyId)
                .header("X-Cosmo-Signature-Alg", SIGNATURE_ALGORITHM)
                .header("X-Cosmo-Signature", signature)
                .build()
        )
    }

    private fun Request.pathAndQuery(): String = url.encodedPath + url.encodedQuery?.let { "?$it" }.orEmpty()

    private fun Request.bodySha256Base64Url(): String {
        val body = body ?: return EMPTY_SHA256
        val buffer = Buffer()
        body.writeTo(buffer)
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(buffer.readByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private companion object {
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        val EMPTY_SHA256: String =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(ByteArray(0)))
    }
}
