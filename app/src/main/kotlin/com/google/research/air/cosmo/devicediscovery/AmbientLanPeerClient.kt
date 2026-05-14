package com.google.research.air.cosmo.devicediscovery

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

interface AmbientLanPeerClient {
    suspend fun getCapabilities(device: AmbientLanDevice): Result<AmbientLanPeerCapabilities>

    suspend fun sendFrames(
        device: AmbientLanDevice,
        batch: AmbientLanFrameBatch
    ): Result<Unit>

    suspend fun fetchFrames(
        device: AmbientLanDevice,
        sinceMillis: Long
    ): Result<AmbientLanFrameBatch>
}

@Singleton
class HttpAmbientLanPeerClient
@Inject
constructor() : AmbientLanPeerClient {
    private val http = OkHttpClient()

    override suspend fun getCapabilities(device: AmbientLanDevice): Result<AmbientLanPeerCapabilities> =
        runCatching {
            val body = get(device, CAPABILITIES_PATH)
            AmbientLanPeerCapabilities.fromJson(JSONObject(body))
        }

    override suspend fun sendFrames(
        device: AmbientLanDevice,
        batch: AmbientLanFrameBatch
    ): Result<Unit> =
        runCatching {
            post(device, EVENTS_PATH, batch.toJson().toString())
        }

    override suspend fun fetchFrames(
        device: AmbientLanDevice,
        sinceMillis: Long
    ): Result<AmbientLanFrameBatch> =
        runCatching {
            val body = get(device, "$EVENTS_PATH?since=$sinceMillis")
            AmbientLanFrameBatch.fromJson(JSONObject(body))
        }

    private fun get(
        device: AmbientLanDevice,
        path: String
    ): String {
        val response =
            http
                .newCall(
                    Request
                        .Builder()
                        .url(device.url(path))
                        .header(HEADER_PROTOCOL, AmbientLanDiscoveryContract.PROTOCOL_VERSION)
                        .get()
                        .build()
                ).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            return it.body.string()
        }
    }

    private fun post(
        device: AmbientLanDevice,
        path: String,
        json: String
    ) {
        val response =
            http
                .newCall(
                    Request
                        .Builder()
                        .url(device.url(path))
                        .header(HEADER_PROTOCOL, AmbientLanDiscoveryContract.PROTOCOL_VERSION)
                        .post(json.toRequestBody(JSON))
                        .build()
                ).execute()
        response.use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
        }
    }

    private fun AmbientLanDevice.url(path: String): String {
        val host = host?.hostAddress ?: error("LAN peer has no host")
        return "http://$host:$port$path"
    }

    companion object {
        const val CAPABILITIES_PATH = "/.well-known/cosmo/ambient/v1/capabilities"
        const val EVENTS_PATH = "/cosmo/ambient/v1/events"
        const val HEADER_PROTOCOL = "X-Cosmo-Protocol"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AmbientLanPeerClientModule {
    @Binds abstract fun bindAmbientLanPeerClient(impl: HttpAmbientLanPeerClient): AmbientLanPeerClient
}
