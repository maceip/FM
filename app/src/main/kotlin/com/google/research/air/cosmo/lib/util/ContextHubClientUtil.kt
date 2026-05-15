package com.google.research.air.cosmo.lib.util

import android.app.Application
import android.util.Log
import com.google.common.util.concurrent.ListeningExecutorService
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

/**
 * Reflective wrapper around the platform `ContextHubManager` (`android.hardware.location.*`).
 * Those classes are `@SystemApi`/`@hide` and **not present in the public Android SDK**, so genesis
 * cannot reference them by symbol at compile time. Cosmo (platform-signed Google internal build)
 * compiles against the framework directly; recovered shape at
 * `~/Downloads/cosmo_baksmali/classes38/com/google/research/air/cosmo/lib/util/ContextHubClientUtil.smali`.
 *
 * Reflection lets genesis attempt the AOC path on Pixel devices that DO expose these APIs
 * (privileged / platform-signed builds, AOSP test images). On a regular APK install the
 * `ACCESS_CONTEXT_HUB` signature permission is denied, every reflective call throws, and
 * [createContextHubClient] returns null. Caller falls back to mic via the false return.
 *
 * The shape mirrors cosmo: pick the first hub, verify the nanoapp is loaded, register a client.
 */
class ContextHubClientUtil
internal constructor(
    /** Real type at runtime: `android.hardware.location.ContextHubManager`. */
    private val contextHubManager: Any,
    private val executor: ListeningExecutorService,
) {
    /**
     * @param callback real type at runtime: `android.hardware.location.ContextHubClientCallback`.
     *                 Caller is responsible for supplying an Object that subclasses the real
     *                 platform class — typically via reflection in [NanoAppClient].
     * @return null on any failure (no hub, nanoapp absent, reflection mismatch).
     *         Real returned type: `android.hardware.location.ContextHubClient`.
     */
    fun createContextHubClient(nanoAppId: Long, callback: Any): Any? {
        val hub = getFirstContextHubInfoOrNull() ?: return null
        if (!findNanoApp(hub, nanoAppId)) return null
        return registerContextHubClient(hub, callback)
    }

    private fun getFirstContextHubInfoOrNull(): Any? =
        runCatching {
            val getContextHubs = contextHubManager.javaClass.getMethod("getContextHubs")
            @Suppress("UNCHECKED_CAST")
            (getContextHubs.invoke(contextHubManager) as? List<Any?>)?.firstOrNull()
        }.onFailure { logReflectionFailure("getContextHubs", it) }.getOrNull()

    private fun findNanoApp(hub: Any, nanoAppId: Long): Boolean =
        runCatching {
            val queryNanoApps =
                contextHubManager.javaClass.getMethod("queryNanoApps", contextHubInfoClass())
            val transaction =
                queryNanoApps.invoke(contextHubManager, hub) ?: return false
            val waitForResponse =
                transaction.javaClass.getMethod(
                    "waitForResponse",
                    java.lang.Long.TYPE,
                    TimeUnit::class.java,
                )
            val response =
                waitForResponse.invoke(transaction, TIMEOUT_QUERY_NANOAPP_SECS, TimeUnit.SECONDS)
                    ?: return false
            val resultCode = response.javaClass.getMethod("getResult").invoke(response) as? Int
            if (resultCode != CONTEXT_HUB_TRANSACTION_RESULT_SUCCESS) {
                Log.w(TAG, "queryNanoApps non-success result=$resultCode")
                return false
            }
            val contents = response.javaClass.getMethod("getContents").invoke(response) as? List<*>
            contents?.any { state ->
                state != null && state.javaClass.getMethod("getNanoAppId").invoke(state) == nanoAppId
            } == true
        }.onFailure { logReflectionFailure("queryNanoApps", it) }.getOrElse { false }

    private fun registerContextHubClient(hub: Any, callback: Any): Any? =
        runCatching {
            val method =
                contextHubManager.javaClass.getMethod(
                    "createClient",
                    contextHubInfoClass(),
                    contextHubClientCallbackClass(),
                    java.util.concurrent.Executor::class.java,
                )
            method.invoke(contextHubManager, hub, callback, executor)
        }.onFailure { logReflectionFailure("createClient", it) }.getOrNull()

    private fun logReflectionFailure(method: String, throwable: Throwable) {
        val cause = (throwable as? InvocationTargetException)?.targetException ?: throwable
        Log.w(TAG, "$method reflective call failed: ${cause::class.simpleName}: ${cause.message}")
    }

    companion object {
        private const val TAG = "ContextHubClientUtil"
        private const val TIMEOUT_QUERY_NANOAPP_SECS = 5L
        private const val CONTEXT_HUB_TRANSACTION_RESULT_SUCCESS = 0

        // Service name string for getSystemService (Context.CONTEXT_HUB_SERVICE is @hide).
        private const val CONTEXT_HUB_SERVICE = "contexthub"

        /**
         * Returns null when the platform has no Context Hub support, the class isn't visible
         * (public SDK build), or `getSystemService` returns null. Mirrors cosmo's
         * `createOrNull(Application, ListeningExecutorService)` factory.
         */
        @JvmStatic
        fun createOrNull(
            application: Application,
            executor: ListeningExecutorService,
        ): ContextHubClientUtil? {
            val manager = runCatching { application.getSystemService(CONTEXT_HUB_SERVICE) }
                .onFailure { Log.i(TAG, "getSystemService(contexthub) failed: ${it.message}") }
                .getOrNull()
            if (manager == null) {
                Log.i(TAG, "ContextHubManager not available on this device")
                return null
            }
            // Sanity check: confirm the runtime class is loadable. If not, this is a pre-API-26
            // device, the framework doesn't expose it, or platform stripped it.
            if (runCatching { contextHubInfoClass() }.isFailure) {
                Log.i(TAG, "ContextHubInfo class not loadable; AOC unavailable")
                return null
            }
            return ContextHubClientUtil(manager, executor)
        }

        @JvmStatic
        @android.annotation.SuppressLint("PrivateApi")
        internal fun contextHubInfoClass(): Class<*> =
            Class.forName("android.hardware.location.ContextHubInfo")

        @JvmStatic
        @android.annotation.SuppressLint("PrivateApi")
        internal fun contextHubClientCallbackClass(): Class<*> =
            Class.forName("android.hardware.location.ContextHubClientCallback")

        @JvmStatic
        @android.annotation.SuppressLint("PrivateApi")
        internal fun nanoAppMessageClass(): Class<*> =
            Class.forName("android.hardware.location.NanoAppMessage")

        @Suppress("unused")
        @JvmStatic
        internal fun createNanoAppMessage(
            nanoAppId: Long,
            messageType: Int,
            messagePayload: ByteArray,
        ): Any? = runCatching {
            val factory = nanoAppMessageClass().getMethod(
                "createMessageToNanoApp",
                java.lang.Long.TYPE,
                java.lang.Integer.TYPE,
                ByteArray::class.java,
            )
            factory.invoke(null, nanoAppId, messageType, messagePayload)
        }.getOrNull()

        @JvmStatic
        internal fun invokeSendMessageToNanoApp(client: Any, message: Any): Int? {
            val method: Method =
                client.javaClass.getMethod("sendMessageToNanoApp", nanoAppMessageClass())
            return method.invoke(client, message) as? Int
        }
    }
}
