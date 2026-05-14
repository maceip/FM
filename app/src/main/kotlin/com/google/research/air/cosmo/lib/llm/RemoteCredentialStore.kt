package com.google.research.air.cosmo.lib.llm

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class RemoteCredentialDataStore

data class StoredAnonymousCredential(
    val authorizationHeader: String,
    val expiresAtEpochMillis: Long,
    val baseUrl: String = "",
    val audience: String = ""
)

interface RemoteCredentialStore {
    suspend fun read(): StoredAnonymousCredential?

    suspend fun write(credential: StoredAnonymousCredential)

    suspend fun clear()
}

@Singleton
class AndroidKeyStoreRemoteCredentialStore
@Inject
constructor(
    @RemoteCredentialDataStore private val dataStore: DataStore<Preferences>
) : RemoteCredentialStore {
    override suspend fun read(): StoredAnonymousCredential? {
        val preferences = dataStore.data.first()
        val encryptedHeader = preferences[Keys.authorizationHeader] ?: return null
        val expiresAt = preferences[Keys.expiresAtEpochMillis] ?: return null
        val header =
            try {
                decrypt(encryptedHeader)
            } catch (e: GeneralSecurityException) {
                Log.w(TAG, "Clearing unreadable remote credential", e)
                clear()
                return null
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Clearing malformed remote credential", e)
                clear()
                return null
            }
        return StoredAnonymousCredential(
            authorizationHeader = header,
            expiresAtEpochMillis = expiresAt,
            baseUrl = preferences[Keys.baseUrl].orEmpty(),
            audience = preferences[Keys.audience].orEmpty()
        )
    }

    override suspend fun write(credential: StoredAnonymousCredential) {
        val encryptedHeader = encrypt(credential.authorizationHeader)
        dataStore.edit { preferences ->
            preferences[Keys.authorizationHeader] = encryptedHeader
            preferences[Keys.expiresAtEpochMillis] = credential.expiresAtEpochMillis
            preferences[Keys.baseUrl] = credential.baseUrl
            preferences[Keys.audience] = credential.audience
        }
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.authorizationHeader)
            preferences.remove(Keys.expiresAtEpochMillis)
            preferences.remove(Keys.baseUrl)
            preferences.remove(Keys.audience)
        }
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return encode(cipher.iv) + PAYLOAD_SEPARATOR + encode(ciphertext)
    }

    private fun decrypt(payload: String): String {
        val pieces = payload.split(PAYLOAD_SEPARATOR, limit = 2)
        require(pieces.size == 2) { "Encrypted credential payload must contain IV and ciphertext" }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, decode(pieces[0])))
        return String(cipher.doFinal(decode(pieces[1])), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore =
            KeyStore
                .getInstance(ANDROID_KEYSTORE)
                .apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private object Keys {
        val authorizationHeader = stringPreferencesKey("authorization_header")
        val expiresAtEpochMillis = longPreferencesKey("expires_at_epoch_millis")
        val baseUrl = stringPreferencesKey("base_url")
        val audience = stringPreferencesKey("audience")
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_ALIAS = "cosmo_remote_credential_store_v1"
        const val PAYLOAD_SEPARATOR = ":"
        const val TAG = "RemoteCredentialStore"
    }
}
