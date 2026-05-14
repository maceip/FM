package com.google.research.air.cosmo.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.Serializer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

class AndroidKeyStoreAead(
    private val keyAlias: String
) {
    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArrayOutputStream(MAGIC.size + 1 + cipher.iv.size + ciphertext.size).use { output ->
            output.write(MAGIC)
            output.write(cipher.iv.size)
            output.write(cipher.iv)
            output.write(ciphertext)
            output.toByteArray()
        }
    }

    fun decrypt(payload: ByteArray): ByteArray {
        require(isEncrypted(payload)) { "Encrypted payload header is missing" }
        val ivSize = payload[MAGIC.size].toInt() and BYTE_MASK
        require(ivSize in MIN_GCM_IV_BYTES..MAX_GCM_IV_BYTES) { "Invalid GCM IV size" }
        val ciphertextOffset = MAGIC.size + 1 + ivSize
        require(payload.size > ciphertextOffset) { "Encrypted payload contains no ciphertext" }
        val iv = payload.copyOfRange(MAGIC.size + 1, ciphertextOffset)
        val ciphertext = payload.copyOfRange(ciphertextOffset, payload.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    fun isEncrypted(payload: ByteArray): Boolean = payload.size > MAGIC.size + 1 && MAGIC.indices.all { index -> payload[index] == MAGIC[index] }

    private fun secretKey(): SecretKey {
        val keyStore =
            KeyStore
                .getInstance(ANDROID_KEYSTORE)
                .apply { load(null) }
        val existing = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val BYTE_MASK = 0xff
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val MAX_GCM_IV_BYTES = 32
        const val MIN_GCM_IV_BYTES = 12
        val MAGIC = byteArrayOf('C'.code.toByte(), 'O'.code.toByte(), 'S'.code.toByte(), 'M'.code.toByte(), 'O'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'C'.code.toByte(), 1)
    }
}

class EncryptedSerializer<T>(
    private val delegate: Serializer<T>,
    private val aead: AndroidKeyStoreAead
) : Serializer<T> {
    override val defaultValue: T = delegate.defaultValue

    override suspend fun readFrom(input: InputStream): T {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return defaultValue
        val plaintext = if (aead.isEncrypted(bytes)) aead.decrypt(bytes) else bytes
        return delegate.readFrom(ByteArrayInputStream(plaintext))
    }

    override suspend fun writeTo(
        t: T,
        output: OutputStream
    ) {
        val plaintext = ByteArrayOutputStream().use { buffer ->
            delegate.writeTo(t, buffer)
            buffer.toByteArray()
        }
        output.write(aead.encrypt(plaintext))
    }
}

interface DatabasePassphraseProvider {
    fun passphrase(): ByteArray
}

@Singleton
class AndroidKeyStoreDatabasePassphraseProvider
@Inject
constructor(
    @ApplicationContext private val context: Context
) : DatabasePassphraseProvider {
    private val aead = AndroidKeyStoreAead(KEY_ALIAS)

    @Synchronized
    override fun passphrase(): ByteArray {
        val file = passphraseFile()
        if (file.exists()) {
            return aead.decrypt(file.readBytes())
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES)
        secureRandom.nextBytes(passphrase)
        writeAtomically(file, aead.encrypt(passphrase))
        return passphrase.copyOf()
    }

    private fun passphraseFile(): File {
        val dir = context.noBackupFilesDir
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Unable to create no-backup directory for database passphrase")
        }
        return File(dir, PASSPHRASE_FILE)
    }

    private fun writeAtomically(
        file: File,
        bytes: ByteArray
    ) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("Unable to commit encrypted database passphrase")
        }
    }

    private companion object {
        const val KEY_ALIAS = "cosmo_context_database_passphrase_v1"
        const val PASSPHRASE_BYTES = 32
        const val PASSPHRASE_FILE = "cosmo_context_database.passphrase"
        val secureRandom = SecureRandom()
    }
}
