package com.google.research.air.cosmo.lib.soda

import android.content.Context
import android.util.Log
import com.google.android.libraries.assistant.soda.Soda
import com.google.android.libraries.assistant.soda.SodaJniLibraryLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared SODA native-runtime probe + language-pack preparer. Stateless aside from the cached
 * "is libsoda_dev_jni loadable" verdict; per-recognition-session state lives in [SodaSession]
 * which each per-source [SodaController] (and [com.google.research.air.cosmo.gatekeeping.GatekeeperDebugReceiver])
 * constructs independently. Mirrors cosmo's `SodaController.smali` pattern: per-source `Soda`
 * instances with their own callbacks, not one shared singleton.
 *
 *  - `isAvailable`: one-time native-lib availability check (cached after first call).
 *  - `prepare()`: resolves the SoC-appropriate language pack via [SodaLanguagePackProvider] and
 *    returns the directory the caller passes to `SodaSession(context, packDir, clock)`.
 */
@Singleton
class JniSodaCapability
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val languagePackProvider: SodaLanguagePackProvider,
) {
    @Volatile
    private var nativeUnavailableReason: String? = null

    val isAvailable: Boolean
        get() {
            nativeUnavailableReason?.let {
                Log.w(TAG, "isAvailable: cached unavailable reason=$it")
                return false
            }
            return runCatching {
                Log.i(TAG, "isAvailable: calling SodaJniLibraryLoader.ensureLoaded()")
                SodaJniLibraryLoader.ensureLoaded()
                Log.i(TAG, "isAvailable: calling Soda.nativeCreateSharedResources")
                val handle = Soda.nativeCreateSharedResources(context)
                Log.i(TAG, "isAvailable: nativeCreateSharedResources returned $handle")
                if (handle != 0L) Soda.nativeDeleteSharedResources(context, handle)
                handle != 0L
            }.fold(
                onSuccess = {
                    Log.i(TAG, "isAvailable: success=$it")
                    it
                },
                onFailure = {
                    nativeUnavailableReason = "soda_native_load_failed:${it.javaClass.simpleName}:${it.message}"
                    Log.e(TAG, "isAvailable: load failed", it)
                    false
                }
            )
        }

    suspend fun prepare(): SodaPrepareResult {
        nativeUnavailableReason?.let { return SodaPrepareResult.Unavailable(it) }
        return when (val pack = languagePackProvider.resolvePackDir()) {
            is SodaPackResolution.Available -> SodaPrepareResult.Available(pack.packDir)
            SodaPackResolution.Unconfigured -> SodaPrepareResult.Unavailable("soda_language_pack_unconfigured")
            is SodaPackResolution.Unavailable -> SodaPrepareResult.Unavailable(pack.reason)
        }
    }

    private companion object {
        const val TAG = "JniSodaCapability"
    }
}
