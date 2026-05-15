package com.google.research.air.cosmo.lib.record

import android.app.Application
import android.util.Log
import com.google.common.util.concurrent.MoreExecutors
import com.google.research.air.cosmo.lib.util.ContextHubClientUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pixel Always-On Compute (AOC) audio binding. Ports cosmo's recovered
 * `~/Downloads/cosmo_baksmali/classes38/com/google/research/air/cosmo/lib/record/NanoAppClient.smali`.
 *
 * Reflection-bridged because `ContextHubManager` and friends are `@SystemApi` (not in the public
 * Android SDK). See [ContextHubClientUtil] for the rationale. On a regular Pixel APK install the
 * `ACCESS_CONTEXT_HUB` signature permission is denied — every reflective dispatch throws and
 * [startRecording] returns false; callers fall back to mic.
 *
 * Lifecycle (when the platform permits the calls):
 *  1. [startRecording] — sends `IPM_MODE_LIVE_STREAM` (0) to the AOC nanoapp via Context Hub.
 *  2. The nanoapp streams 16 kHz mono PCM16 frames back through `onMessageFromNanoApp`. Cosmo's
 *     `processNanoappMessage` forwards the NanoAppMessage payload directly to
 *     `RecordServiceListener.onBytesReceived` — no protocol decoding, the payload IS the PCM.
 *  3. Silence injection: if no real audio arrives within [SILENCE_TIMEOUT_MS], we start writing
 *     1 sec of zero-bytes every [SILENCE_INJECTION_INTERVAL_MS] until either real audio resumes
 *     (resets the session token) or [MAX_SILENCE_INJECTION_MS] total is hit. The nanoapp times
 *     itself out without periodic activity; silence keeps the session alive in quiet rooms.
 *  4. [stopRecording] — sends `IPM_MODE_STREAMING_OFF` (3).
 */
@Singleton
class NanoAppClient
@Inject
constructor(
    private val application: Application,
    private val scheduler: ScheduledExecutorService,
) {
    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    private val silenceBytes = ByteArray(SILENCE_BUFFER_SIZE)
    private val contextHubClientUtil: ContextHubClientUtil? =
        ContextHubClientUtil.createOrNull(application, executor)
    private val contextHubClientCallback: Any? = buildContextHubClientCallback()

    @Volatile
    var recordListener: RecordServiceListener? = null

    @Volatile
    private var contextHubClient: Any? = null

    @Volatile
    private var silenceInjectionFuture: ScheduledFuture<*>? = null

    @Volatile
    private var currentSessionToken: Int = 0

    @Volatile
    private var totalSilenceInjectedMs: Long = 0

    /** Sends LIVE_STREAM to the nanoapp. Returns true if the message was accepted. */
    fun startRecording(): Boolean {
        if (contextHubClientUtil == null || contextHubClientCallback == null) {
            Log.i(TAG, "CHRE is not supported on this device; AOC unavailable")
            return false
        }
        val accepted = sendMessageToNanoApp(IPM_MODE_LIVE_STREAM)
        if (accepted) resetSilenceInjection()
        return accepted
    }

    /** Sends STREAMING_OFF and cancels silence injection. */
    @Synchronized
    fun stopRecording(): Boolean {
        silenceInjectionFuture?.cancel(false)
        silenceInjectionFuture = null
        currentSessionToken += 1
        totalSilenceInjectedMs = 0
        val sent = sendMessageToNanoApp(IPM_MODE_STREAMING_OFF)
        recordListener?.onStopRecording(sent)
        return sent
    }

    private fun processNanoappMessage(messageBody: ByteArray, @Suppress("UNUSED_PARAMETER") messageType: Int) {
        recordListener?.onBytesReceived(messageBody)
        resetSilenceInjection()
    }

    @Synchronized
    private fun resetSilenceInjection() {
        currentSessionToken += 1
        silenceInjectionFuture?.cancel(false)
        totalSilenceInjectedMs = 0
        val token = currentSessionToken
        silenceInjectionFuture =
            scheduler.schedule(
                { injectSilence(token) },
                SILENCE_TIMEOUT_MS,
                TimeUnit.MILLISECONDS,
            )
    }

    @Synchronized
    private fun injectSilence(sessionToken: Int) {
        if (sessionToken != currentSessionToken) return
        if (totalSilenceInjectedMs >= MAX_SILENCE_INJECTION_MS) {
            Log.w(TAG, "Silence injection cap hit ($MAX_SILENCE_INJECTION_MS ms); stopping AOC")
            stopRecording()
            return
        }
        recordListener?.onBytesReceived(silenceBytes)
        totalSilenceInjectedMs += SILENCE_INJECTION_INTERVAL_MS
        silenceInjectionFuture =
            scheduler.schedule(
                { injectSilence(sessionToken) },
                SILENCE_INJECTION_INTERVAL_MS,
                TimeUnit.MILLISECONDS,
            )
    }

    @Suppress("ReturnCount")
    private fun sendMessageToNanoApp(message: Int): Boolean {
        val util = contextHubClientUtil ?: run {
            Log.i(TAG, "CHRE is not supported.")
            return false
        }
        val callback = contextHubClientCallback ?: return false
        val client =
            contextHubClient ?: util.createContextHubClient(AMBIENT_AUDIO_NANO_APP_ID, callback)?.also {
                contextHubClient = it
            } ?: run {
                Log.w(TAG, "Failed to register Context Hub client for nanoapp $AMBIENT_AUDIO_NANO_APP_ID")
                return false
            }
        val payload = intToLeBytes(message)
        val nanoAppMessage =
            ContextHubClientUtil.createNanoAppMessage(AMBIENT_AUDIO_NANO_APP_ID, message, payload)
                ?: return false
        val result =
            runCatching { ContextHubClientUtil.invokeSendMessageToNanoApp(client, nanoAppMessage) }
                .onFailure { Log.w(TAG, "sendMessageToNanoApp threw: ${it.message}") }
                .getOrElse { return false }
        if (result != IPM_SOUND_OK_STATUS) {
            Log.w(TAG, "sendMessageToNanoApp non-OK result=$result")
            return false
        }
        return true
    }

    /**
     * Build a `ContextHubClientCallback` subclass instance via [Proxy]. We can't subclass the
     * `@SystemApi` abstract class directly at compile time, so we proxy its interface methods
     * (`onMessageFromNanoApp`, `onHubReset`, `onNanoAppAborted`, `onNanoAppLoaded`) reflectively.
     *
     * NOTE: `ContextHubClientCallback` is a class, not an interface. `Proxy.newProxyInstance`
     * cannot wrap an abstract class. On a real platform-signed build, this method should be
     * replaced with a compile-time subclass. For genesis's current public-SDK build we instead
     * generate a same-named anonymous subclass via [java.lang.reflect.Proxy] over the
     * `InvocationHandler` interface, which silently fails on platforms where the class isn't
     * loadable (i.e., the vast majority of stock devices). When that proxy creation fails the
     * call returns null and the AOC path is permanently disabled for the session — caller falls
     * back to mic.
     *
     * If you have access to a platform-signed build, replace this method with:
     * ```kotlin
     * object : ContextHubClientCallback() {
     *     override fun onMessageFromNanoApp(client: ContextHubClient, message: NanoAppMessage) {
     *         processNanoappMessage(message.messageBody, message.messageType)
     *     }
     *     ...
     * }
     * ```
     */
    private fun buildContextHubClientCallback(): Any? {
        val callbackClass = runCatching { ContextHubClientUtil.contextHubClientCallbackClass() }
            .onFailure { Log.i(TAG, "ContextHubClientCallback not loadable; AOC disabled") }
            .getOrNull() ?: return null
        // Java reflection can only proxy interfaces. ContextHubClientCallback is a class, so we
        // can't synthesize a subclass without bytecode generation (ASM / Javassist) — which
        // would be ~30+ KB of dependency for a code path that won't run on stock devices anyway.
        // Document the limitation; return null so [startRecording] returns false at runtime.
        Log.i(TAG, "ContextHubClientCallback is a class (${callbackClass.name}); cannot subclass " +
            "without bytecode gen. AOC requires a platform-signed build with a compile-time subclass.")
        return null
    }

    private fun intToLeBytes(value: Int): ByteArray =
        byteArrayOf(
            (value and 0xff).toByte(),
            ((value ushr 8) and 0xff).toByte(),
            ((value ushr 16) and 0xff).toByte(),
            ((value ushr 24) and 0xff).toByte(),
        )

    /**
     * Test seam: hook used by [NanoAppClientTest] to deliver a synthetic nanoapp message into
     * the processing pipeline without owning a real Context Hub. Not used in production.
     */
    @Suppress("unused")
    internal fun deliverMessageForTest(messageBody: ByteArray, messageType: Int) {
        processNanoappMessage(messageBody, messageType)
    }

    /** Test seam: invoked by [NanoAppClientTest] to drive silence injection deterministically. */
    @Suppress("unused")
    internal fun injectSilenceForTest() {
        injectSilence(currentSessionToken)
    }

    companion object {
        private const val TAG = "NanoAppClient"

        /** Vendor-prefixed AOC ambient-audio nanoapp ID: `Googl\x00\x10\x68` per cosmo smali. */
        const val AMBIENT_AUDIO_NANO_APP_ID: Long = 0x476f6f676c001068L

        private const val IPM_MODE_LIVE_STREAM: Int = 0
        private const val IPM_MODE_STREAMING_OFF: Int = 3
        private const val IPM_SOUND_OK_STATUS: Int = 0

        const val SILENCE_INJECTION_INTERVAL_MS: Long = 1000L
        const val SILENCE_TIMEOUT_MS: Long = 2500L
        private const val MAX_SILENCE_INJECTION_MS: Long = 8000L

        /** 1 sec of 16 kHz mono PCM16 = 32,000 bytes (per cosmo `AudioUtil`). */
        private const val SILENCE_BUFFER_SIZE: Int = 32_000
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NanoAppClientModule {
    /**
     * Single-threaded scheduler used for AOC silence-injection. Cosmo's NanoAppClient takes a
     * `ScheduledExecutorService` via constructor; bind one app-scoped here.
     */
    @Provides
    @Singleton
    fun provideAocScheduler(): ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
}

@Suppress("unused")
private class NanoAppCallbackHandler(
    private val onMessage: (Any) -> Unit,
    private val onReset: () -> Unit,
    private val onAborted: (Long, Int) -> Unit,
) : InvocationHandler {
    override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
        when (method.name) {
            "onMessageFromNanoApp" -> args?.getOrNull(1)?.let(onMessage)
            "onHubReset" -> onReset()
            "onNanoAppAborted" -> {
                val nanoAppId = args?.getOrNull(1) as? Long ?: return null
                val abortCode = args.getOrNull(2) as? Int ?: return null
                onAborted(nanoAppId, abortCode)
            }
        }
        return null
    }
}

@Suppress("unused")
private fun newProxyForCallback(handler: NanoAppCallbackHandler, callbackInterface: Class<*>): Any =
    Proxy.newProxyInstance(callbackInterface.classLoader, arrayOf(callbackInterface), handler)
