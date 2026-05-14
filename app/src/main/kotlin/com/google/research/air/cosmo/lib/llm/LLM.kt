package com.google.research.air.cosmo.lib.llm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.research.air.cosmo.di.ApplicationScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class LocalLLM

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ServerLLM

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class HybridLLM

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SimulatedLLM

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AICoreReady

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class LlmStatsDataStore

sealed class PromptPart {
    data class Text(
        val text: String
    ) : PromptPart()

    data class Audio(
        val audioBytes: ByteArray,
        val mimeType: String
    ) : PromptPart()

    data class Image(
        val imageBytes: ByteArray,
        val mimeType: String
    ) : PromptPart()
}

interface LLM {
    suspend fun generateText(prompt: String): String {
        val tokens = StringBuilder()
        inference(prompt) { tokens.append(it) }
        return tokens.toString()
    }

    suspend fun inference(
        prompt: String,
        onToken: (String) -> Unit
    ): String

    suspend fun inference(
        parts: List<PromptPart>,
        onToken: (String) -> Unit
    ): String
}

enum class LlmFailureCause {
    AICORE_NOT_INSTALLED,
    MODEL_NOT_READY,
    QUOTA,
    NETWORK,
    AUTH,
    TIMEOUT,
    BAD_CERTIFICATE,
    CONFIGURATION,
    LOCAL_MODEL_UNAVAILABLE,
    INTERNAL
}

class LlmUnavailableException(
    val reason: LlmFailureCause,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

class AICoreLLM
@Inject
constructor(
    private val client: AICoreClientWrapper
) : LlmBackendAdapter(
    backendId = LlmBackendId.AICORE,
    capabilities = LlmBackendCapabilities.TEXT_ONLY
) {
    override suspend fun generateSupported(
        request: LlmRequest,
        onToken: (String) -> Unit
    ): String = client.generate(request.requireTextPrompt(backendId), onToken)
}

class RemoteInferenceLLM
@Inject
constructor(
    private val client: RemoteInferenceClient,
    private val statsRepository: LLMStatsStore
) : LlmBackendAdapter(
    backendId = LlmBackendId.REMOTE_INFERENCE,
    capabilities = LlmBackendCapabilities.TEXT_IMAGE_AUDIO
) {
    override suspend fun generateSupported(
        request: LlmRequest,
        onToken: (String) -> Unit
    ): String {
        statsRepository.incrementHit()
        return try {
            val text = client.infer(request.parts, onToken)
            statsRepository.incrementSuccess()
            statsRepository.updateRemoteInferenceEnabled(true)
            text
        } catch (e: Exception) {
            statsRepository.incrementFailure()
            statsRepository.updateRemoteInferenceEnabled(false)
            throw e
        }
    }
}

interface LLMRouteState {
    val activePath: StateFlow<ModelPath>
    val routeDecision: StateFlow<LLMRouteDecision>
}

@Singleton
class NetworkAwareLLM
@Inject
constructor(
    @LocalLLM private val local: LLM,
    @ServerLLM private val remote: LLM,
    private val networkPolicyProvider: NetworkPolicyProvider
) : LlmBackendAdapter(
    backendId = LlmBackendId.NETWORK_AWARE,
    capabilities = LlmBackendCapabilities.TEXT_IMAGE_AUDIO
),
    LLMRouteState {
    private val _activePath = MutableStateFlow(ModelPath.LOCAL_LLM)
    private val _routeDecision =
        MutableStateFlow(
            LLMRouteDecision(
                path = ModelPath.LOCAL_LLM,
                reason = LLMRouteReason.NETWORK_UNAVAILABLE,
                networkPolicy = NetworkPolicy.UNAVAILABLE
            )
        )
    override val activePath: StateFlow<ModelPath> = _activePath
    override val routeDecision: StateFlow<LLMRouteDecision> = _routeDecision

    override suspend fun generateSupported(
        request: LlmRequest,
        onToken: (String) -> Unit
    ): String {
        val decision = selectRoute()
        return executeLocalFirst(
            decision = decision,
            onToken = onToken,
            remoteCall = { callback -> remote.generateRequest(request, callback) },
            localCall = { callback -> local.generateRequest(request, callback) }
        )
    }

    private suspend fun executeLocalFirst(
        decision: LLMRouteDecision,
        onToken: (String) -> Unit,
        remoteCall: suspend ((String) -> Unit) -> String,
        localCall: suspend ((String) -> Unit) -> String
    ): String {
        var localEmitted = false
        val localDecision =
            if (decision.path == ModelPath.CLOUD_LLM) {
                decision.copy(
                    path = ModelPath.LOCAL_LLM,
                    reason = LLMRouteReason.LOCAL_ELIGIBLE
                )
            } else {
                decision
            }
        publishRouteDecision(localDecision)
        return try {
            localCall { token ->
                localEmitted = true
                onToken(token)
            }
        } catch (localFailure: LlmUnavailableException) {
            if (localEmitted || decision.path == ModelPath.LOCAL_LLM) throw localFailure
            publishRouteDecision(
                decision.copy(
                    path = ModelPath.CLOUD_LLM,
                    reason = LLMRouteReason.LOCAL_UNAVAILABLE_REMOTE_ELIGIBLE
                )
            )
            executeRemoteFallback(remoteCall, onToken, localFailure, decision)
        }
    }

    private suspend fun executeRemoteFallback(
        remoteCall: suspend ((String) -> Unit) -> String,
        onToken: (String) -> Unit,
        localFailure: LlmUnavailableException,
        decision: LLMRouteDecision
    ): String =
        try {
            remoteCall(onToken)
        } catch (remoteFailure: LlmUnavailableException) {
            remoteFailure.addSuppressed(localFailure)
            publishRouteDecision(
                decision.copy(
                    path = ModelPath.LOCAL_LLM,
                    reason = LLMRouteReason.REMOTE_UNAVAILABLE
                )
            )
            throw remoteFailure
        }

    private fun selectRoute(): LLMRouteDecision {
        val network = networkPolicyProvider.currentNetworkPolicy()
        val decision =
            when {
                !network.available ->
                    LLMRouteDecision(ModelPath.LOCAL_LLM, LLMRouteReason.NETWORK_UNAVAILABLE, network)
                !network.validated ->
                    LLMRouteDecision(ModelPath.LOCAL_LLM, LLMRouteReason.NETWORK_UNVALIDATED, network)
                network.vpnActive ->
                    LLMRouteDecision(ModelPath.LOCAL_LLM, LLMRouteReason.VPN_ACTIVE, network)
                network.downstreamKbps < MIN_REMOTE_DOWNSTREAM_KBPS ->
                    LLMRouteDecision(ModelPath.LOCAL_LLM, LLMRouteReason.DOWNSTREAM_TOO_LOW, network)
                network.upstreamKbps < MIN_REMOTE_UPSTREAM_KBPS ->
                    LLMRouteDecision(ModelPath.LOCAL_LLM, LLMRouteReason.UPSTREAM_TOO_LOW, network)
                else ->
                    LLMRouteDecision(ModelPath.CLOUD_LLM, LLMRouteReason.REMOTE_ELIGIBLE, network)
            }
        publishRouteDecision(decision)
        return decision
    }

    private fun publishRouteDecision(decision: LLMRouteDecision) {
        _routeDecision.value = decision
        _activePath.value = decision.path
    }

    companion object {
        private const val MIN_REMOTE_DOWNSTREAM_KBPS = 512
        private const val MIN_REMOTE_UPSTREAM_KBPS = 128
    }
}

enum class ModelPath { CLOUD_LLM, LOCAL_LLM }

enum class LLMRouteReason {
    LOCAL_ELIGIBLE,
    LOCAL_UNAVAILABLE_REMOTE_ELIGIBLE,
    REMOTE_ELIGIBLE,
    NETWORK_UNAVAILABLE,
    NETWORK_UNVALIDATED,
    VPN_ACTIVE,
    DOWNSTREAM_TOO_LOW,
    UPSTREAM_TOO_LOW,
    REMOTE_UNAVAILABLE
}

data class NetworkPolicy(
    val available: Boolean,
    val validated: Boolean,
    val unmetered: Boolean,
    val vpnActive: Boolean,
    val downstreamKbps: Int,
    val upstreamKbps: Int
) {
    companion object {
        val UNAVAILABLE = NetworkPolicy(false, false, false, false, 0, 0)
    }
}

data class LLMRouteDecision(
    val path: ModelPath,
    val reason: LLMRouteReason,
    val networkPolicy: NetworkPolicy
)

object NetworkUtils {
    fun currentNetworkPolicy(context: Context): NetworkPolicy {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }
        return capabilities?.let {
            NetworkPolicy(
                available = it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                validated = it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                unmetered = it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
                vpnActive = it.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                downstreamKbps = it.linkDownstreamBandwidthKbps,
                upstreamKbps = it.linkUpstreamBandwidthKbps
            )
        } ?: NetworkPolicy.UNAVAILABLE
    }
}

interface NetworkPolicyProvider {
    fun currentNetworkPolicy(): NetworkPolicy
}

class AndroidNetworkPolicyProvider
@Inject
constructor(
    @ApplicationContext private val context: Context
) : NetworkPolicyProvider {
    override fun currentNetworkPolicy(): NetworkPolicy = NetworkUtils.currentNetworkPolicy(context)
}

data class LLMInferenceStats(
    val hit: Int,
    val success: Int,
    val failure: Int
)

interface LLMStatsStore {
    val inferenceStats: StateFlow<LLMInferenceStats>
    val isRemoteInferenceEnabled: StateFlow<Boolean>

    suspend fun refreshFromDisk()

    fun incrementHit()

    fun incrementSuccess()

    fun incrementFailure()

    fun reset()

    fun updateRemoteInferenceEnabled(enabled: Boolean)

    fun recentSuccessCount(): Int

    fun recentTotalCount(): Int
}

@Singleton
class LLMStatsRepository
@Inject
constructor(
    @LlmStatsDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope
) : LLMStatsStore {
    private val recentCalls = ArrayDeque<Boolean>()
    private val _inferenceStats = MutableStateFlow(LLMInferenceStats(0, 0, 0))
    private val _remoteInferenceEnabled = MutableStateFlow(false)
    override val inferenceStats: StateFlow<LLMInferenceStats> = _inferenceStats
    override val isRemoteInferenceEnabled: StateFlow<Boolean> = _remoteInferenceEnabled

    init {
        scope.launch { refreshFromDisk() }
    }

    override suspend fun refreshFromDisk() {
        val prefs = dataStore.data.first()
        _inferenceStats.value = prefs.toStats()
        _remoteInferenceEnabled.value = prefs[REMOTE_INFERENCE_ENABLED] ?: false
    }

    @Synchronized override fun incrementHit() {
        val next = _inferenceStats.value.copy(hit = _inferenceStats.value.hit + 1)
        _inferenceStats.value = next
        incrementPersistedCounter(HIT)
    }

    @Synchronized override fun incrementSuccess() {
        recentCalls.addLast(true)
        trimRecent()
        val next = _inferenceStats.value.copy(success = _inferenceStats.value.success + 1)
        _inferenceStats.value = next
        incrementPersistedCounter(SUCCESS)
    }

    @Synchronized override fun incrementFailure() {
        recentCalls.addLast(false)
        trimRecent()
        val next = _inferenceStats.value.copy(failure = _inferenceStats.value.failure + 1)
        _inferenceStats.value = next
        incrementPersistedCounter(FAILURE)
    }

    @Synchronized override fun reset() {
        recentCalls.clear()
        _inferenceStats.value = LLMInferenceStats(0, 0, 0)
        scope.launch {
            dataStore.edit {
                it[HIT] = 0
                it[SUCCESS] = 0
                it[FAILURE] = 0
            }
        }
    }

    override fun updateRemoteInferenceEnabled(enabled: Boolean) {
        _remoteInferenceEnabled.value = enabled
        scope.launch { dataStore.edit { it[REMOTE_INFERENCE_ENABLED] = enabled } }
    }

    override fun recentSuccessCount(): Int = recentCalls.count { it }

    override fun recentTotalCount(): Int = recentCalls.size

    private fun incrementPersistedCounter(key: Preferences.Key<Int>) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[key] = (prefs[key] ?: 0) + 1
                _inferenceStats.value = prefs.toStats()
            }
        }
    }

    private fun Preferences.toStats(): LLMInferenceStats = LLMInferenceStats(
        hit = this[HIT] ?: 0,
        success = this[SUCCESS] ?: 0,
        failure = this[FAILURE] ?: 0
    )

    private fun trimRecent() {
        while (recentCalls.size > RECENT_LIMIT) recentCalls.removeFirst()
    }

    companion object {
        private const val RECENT_LIMIT = 50
        internal val HIT = intPreferencesKey("hit")
        internal val SUCCESS = intPreferencesKey("success")
        internal val FAILURE = intPreferencesKey("failure")
        internal val REMOTE_INFERENCE_ENABLED = booleanPreferencesKey("remote_inference_enabled")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object LLMModule {
    @Provides @Singleton @LlmStatsDataStore
    fun provideStatsDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("llm_stats.preferences_pb") }
    )

    @Provides @Singleton
    fun provideAicoreWrapper(impl: EdgeAICoreClientWrapper): AICoreClientWrapper = impl

    @Provides @Singleton
    @RemoteCredentialDataStore
    fun provideRemoteCredentialDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("remote_credentials.preferences_pb") }
    )

    @Provides @Singleton
    fun provideRemoteCredentialStore(impl: AndroidKeyStoreRemoteCredentialStore): RemoteCredentialStore = impl

    @Provides @Singleton
    fun provideRequestSigner(impl: AndroidKeyStoreRequestSigner): RequestSigner = impl

    @Provides @Singleton
    fun provideAnonymousCredentialIssuerClient(impl: OkHttpAnonymousCredentialIssuerClient): AnonymousCredentialIssuerClient = impl

    @Provides @Singleton
    fun provideAnonymousCredentialProvider(impl: RefreshingAnonymousCredentialProvider): AnonymousCredentialProvider = impl

    @Provides @Singleton
    fun provideRemoteInferenceClient(impl: OkHttpRemoteInferenceClient): RemoteInferenceClient = impl

    @Provides @Singleton
    fun provideNetworkPolicyProvider(impl: AndroidNetworkPolicyProvider): NetworkPolicyProvider = impl

    @Provides @Singleton
    fun provideLLMStatsStore(impl: LLMStatsRepository): LLMStatsStore = impl

    @Provides @Singleton
    fun provideLLMRouteState(impl: NetworkAwareLLM): LLMRouteState = impl

    @Provides @Singleton @AICoreReady
    fun provideAicoreReady(wrapper: AICoreClientWrapper): Boolean = wrapper.isAvailable()

    @Provides @Singleton @LocalLLM
    fun provideLocal(local: CascadingLocalLLM): LLM = local

    @Provides @Singleton @ServerLLM
    fun provideServer(
        client: RemoteInferenceClient,
        stats: LLMStatsStore
    ): LLM = RemoteInferenceLLM(client, stats)

    @Provides @Singleton @HybridLLM
    fun provideHybrid(llm: NetworkAwareLLM): LLM = llm

    @Provides @Singleton @SimulatedLLM
    fun provideSimulation(): LLM = SimulationLLM()

    @Provides @Singleton
    fun provideDefault(
        @HybridLLM llm: LLM
    ): LLM = llm
}
