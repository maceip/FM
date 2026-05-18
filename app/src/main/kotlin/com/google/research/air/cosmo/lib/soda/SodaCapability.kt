package com.google.research.air.cosmo.lib.soda

import android.content.Context
import android.util.Log
import com.google.research.air.cosmo.runtime.CosmoBuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-device SODA capability surface. Mirrors the recovered cosmo `SodaWiring` /
 * `Soda.addAudio(ByteBuffer, int)` pattern from `cosmo-soda-interop`. Genesis binds
 * `JniSodaCapability` for internal/debug PoC builds and fails closed when the native runtime or
 * selected language pack cannot be prepared.
 *
 * Lifecycle:
 *   1. `prepare(context)`: ensure the SoC-appropriate language pack is extracted/cached.
 *   2. `start(callback)`: initialize SODA + start capture; transcripts arrive on the callback.
 *   3. `addAudio(buffer, length)`: stream PCM 16-bit (16 kHz mono, native-byte-order) frames.
 *   4. `stop()` / `close()`.
 */
/**
 * SODA capability surface. Cosmo's `SodaController.smali` doesn't go through a separate
 * `SodaCapability` interface — the JNI lifecycle methods live directly on the controller. We
 * deleted the interface (architecture-delta C4 row 4: ~12-type → ~3-type consolidation) because
 * it had exactly one production binding (`JniSodaCapability`) and one no-op test stand-in. The
 * remaining types ([SodaTranscriptCallback], [SodaPrepareResult], [SodaStartResult]) are
 * operation contracts that exist as standalone types because they're shared between
 * `JniSodaCapability` and `SodaController` — folding them into [JniSodaCapability] would force
 * fully-qualified names everywhere they appear.
 */
fun interface SodaTranscriptCallback {
    fun onTranscript(text: String, isFinal: Boolean)

    fun onDirectednessTrigger(at: Instant) = Unit
}

sealed class SodaPrepareResult {
    data class Available(val packDir: File) : SodaPrepareResult()

    data class Unavailable(
        val reason: String
    ) : SodaPrepareResult()
}

sealed class SodaStartResult {
    object Started : SodaStartResult()

    data class Failed(
        val reason: String
    ) : SodaStartResult()
}

/**
 * Manages download, checksum, and extraction for the SODA language pack. The recovered
 * `LanguagePackManager.smali` has SoC-specific TPU constants, but those packs currently fail
 * `initSoda` with this JNI/runtime combination on Pixel 10. The launch path therefore uses
 * the CPU pack on all devices until the TPU packs are proven by the device fixture harness.
 *
 * Each pack is configured via Gradle properties (`cosmo.soda.languagePack.*`) so operators can
 * swap hosting without rebuilding. Resolution order: bundled APK asset → app-private cache →
 * runtime HTTPS download. When no pack is provisioned, `prepare()` returns `Unavailable` and SODA
 * fails closed without silently falling back to server or framework ASR.
 */
@Singleton
class SodaLanguagePackProvider
@Inject
constructor(
    @ApplicationContext private val appContext: Context
) {
    private val downloadMutex = Mutex()

    suspend fun resolvePackDir(): SodaPackResolution {
        val descriptor = pickDescriptor() ?: return SodaPackResolution.Unconfigured
        val cacheDir = File(appContext.cacheDir, "soda_packs/${descriptor.name}")
        val sentinel = File(cacheDir, ".extracted")
        if (sentinel.exists()) {
            return SodaPackResolution.Available(cacheDir, descriptor)
        }
        val zipFile = obtainZip(descriptor) ?: return SodaPackResolution.Unavailable(descriptor, "soda_pack_zip_unavailable")
        return downloadMutex.withLock {
            if (sentinel.exists()) return@withLock SodaPackResolution.Available(cacheDir, descriptor)
            cacheDir.mkdirs()
            try {
                val canonicalRoot = cacheDir.canonicalFile
                ZipInputStream(zipFile.inputStream()).use { zin ->
                    while (true) {
                        val entry = zin.nextEntry ?: break
                        val out = safeZipEntryFile(canonicalRoot, entry.name)
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { fout -> zin.copyTo(fout) }
                        }
                        zin.closeEntry()
                    }
                }
                sentinel.writeText("ok")
                SodaPackResolution.Available(cacheDir, descriptor)
            } catch (e: IOException) {
                cacheDir.deleteRecursively()
                SodaPackResolution.Unavailable(descriptor, "soda_pack_extract_failed:${e.javaClass.simpleName}")
            }
        }
    }

    private fun pickDescriptor(): SodaPackDescriptor? = describeFromConfig(SodaPackKind.CPU)

    private fun describeFromConfig(kind: SodaPackKind): SodaPackDescriptor? {
        val url = CosmoBuildConfig.sodaPackUrl(kind).trim()
        val sha = CosmoBuildConfig.sodaPackSha256(kind).trim()
        val size = CosmoBuildConfig.sodaPackSizeBytes(kind)
        if (sha.isBlank() || size <= 0L) return null
        return SodaPackDescriptor(kind, kind.assetPath, url, sha, size)
    }

    private suspend fun obtainZip(descriptor: SodaPackDescriptor): File? {
        bundledAsset(descriptor)?.let { return it }
        val cached = File(appContext.filesDir, "soda_packs/${descriptor.kind.assetName}")
        if (cached.length() == descriptor.sizeBytes && sha256(cached).equals(descriptor.sha256, ignoreCase = true)) {
            return cached
        }
        if (descriptor.url.isBlank()) return null
        return downloadAndVerify(descriptor, cached)
    }

    private fun bundledAsset(descriptor: SodaPackDescriptor): File? {
        val staged = File(appContext.filesDir, "soda_packs/staged_${descriptor.kind.assetName}")
        staged.parentFile?.mkdirs()
        if (matchesDescriptor(staged, descriptor)) return staged
        return try {
            copyAssetTo(descriptor, staged)
            if (matchesDescriptor(staged, descriptor)) staged else { staged.delete(); null }
        } catch (_: IOException) {
            null
        }
    }

    private fun copyAssetTo(descriptor: SodaPackDescriptor, staged: File) {
        appContext.assets.open(descriptor.assetPath).use { input ->
            staged.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun matchesDescriptor(file: File, descriptor: SodaPackDescriptor): Boolean =
        file.length() == descriptor.sizeBytes && sha256(file).equals(descriptor.sha256, ignoreCase = true)

    private fun safeZipEntryFile(
        canonicalRoot: File,
        entryName: String
    ): File {
        val output = File(canonicalRoot, entryName).canonicalFile
        val rootPath = canonicalRoot.path + File.separator
        if (output != canonicalRoot && !output.path.startsWith(rootPath)) {
            throw IOException("SODA pack entry escapes target directory: $entryName")
        }
        return output
    }

    private suspend fun downloadAndVerify(descriptor: SodaPackDescriptor, target: File): File? = withContext(Dispatchers.IO) {
        val parent = target.parentFile ?: return@withContext null
        if (!parent.exists() && !parent.mkdirs()) return@withContext null
        val temp = File(parent, target.name + ".part")
        try {
            val connection = URL(descriptor.url).openConnection() as HttpURLConnection
            try {
                connection.run {
                    connectTimeout = HTTP_CONNECT_TIMEOUT_MS
                    readTimeout = HTTP_READ_TIMEOUT_MS
                    requestMethod = "GET"
                    connect()
                    if (responseCode !in 200..299) {
                        return@withContext null
                    }
                    inputStream.use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            } finally {
                connection.disconnect()
            }
            if (temp.length() != descriptor.sizeBytes) {
                temp.delete()
                return@withContext null
            }
            if (!sha256(temp).equals(descriptor.sha256, ignoreCase = true)) {
                temp.delete()
                return@withContext null
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                return@withContext null
            }
            target
        } catch (e: IOException) {
            Log.w(TAG, "soda pack download failed: ${e.message}")
            if (temp.exists()) temp.delete()
            null
        }
    }

    private fun sha256(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(SHA_BUFFER_BYTES)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            val unsigned = byte.toInt() and 0xff
            unsigned.toString(HEX_RADIX).padStart(2, '0')
        }
    }

    private companion object {
        const val HTTP_CONNECT_TIMEOUT_MS = 30_000
        const val HTTP_READ_TIMEOUT_MS = 30_000
        const val SHA_BUFFER_BYTES = 1 shl 16
        const val HEX_RADIX = 16
        const val TAG = "SodaLanguagePackProvider"
    }
}

enum class SodaPackKind(val assetName: String, val assetPath: String) {
    CPU("lp_cpu.zip", "soda/lp_cpu.zip"),
    LGA_TPU("lp_lga_tpu.zip", "soda/lp_lga_tpu.zip"),
    ZUMA_TPU("lp_zuma_tpu.zip", "soda/lp_zuma_tpu.zip")
}

data class SodaPackDescriptor(
    val kind: SodaPackKind,
    val assetPath: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long
) {
    val name: String = kind.assetName.removeSuffix(".zip")
}

sealed class SodaPackResolution {
    object Unconfigured : SodaPackResolution()

    data class Available(
        val packDir: File,
        val descriptor: SodaPackDescriptor
    ) : SodaPackResolution()

    data class Unavailable(
        val descriptor: SodaPackDescriptor,
        val reason: String
    ) : SodaPackResolution()
}
