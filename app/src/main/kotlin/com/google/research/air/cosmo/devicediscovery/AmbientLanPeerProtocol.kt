package com.google.research.air.cosmo.devicediscovery

import android.util.Base64
import com.google.research.air.cosmo.ephemeralbuffer.EphemeralBufferId
import org.json.JSONArray
import org.json.JSONObject

enum class AmbientLanPayloadKind {
    BYTES,
    TEXT,
    JSON
}

data class AmbientLanPeerCapabilities(
    val protocol: String = AmbientLanDiscoveryContract.PROTOCOL_VERSION,
    val role: String = AmbientLanDiscoveryContract.ROLE_ANDROID_PEER,
    val deviceId: String,
    val supportedBuffers: Set<EphemeralBufferId>
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("protocol", protocol)
            .put("role", role)
            .put("device_id", deviceId)
            .put("supported_buffers", JSONArray(supportedBuffers.map { it.name }))

    companion object {
        fun fromJson(json: JSONObject): AmbientLanPeerCapabilities =
            AmbientLanPeerCapabilities(
                protocol = json.optString("protocol", AmbientLanDiscoveryContract.PROTOCOL_VERSION),
                role = json.optString("role", AmbientLanDiscoveryContract.ROLE_ANDROID_PEER),
                deviceId = json.optString("device_id", "unknown"),
                supportedBuffers =
                    json
                        .optJSONArray("supported_buffers")
                        .toStringList()
                        .mapNotNull { runCatching { EphemeralBufferId.valueOf(it) }.getOrNull() }
                        .toSet()
            )
    }
}

data class AmbientLanFrame(
    val id: String,
    val bufferId: EphemeralBufferId,
    val kind: AmbientLanPayloadKind,
    val timestampMillis: Long,
    val payload: ByteArray,
    val metadata: Map<String, String> = emptyMap()
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("buffer_id", bufferId.name)
            .put("kind", kind.name)
            .put("timestamp_millis", timestampMillis)
            .put("payload_base64", Base64.encodeToString(payload, Base64.NO_WRAP))
            .put("metadata", JSONObject(metadata))

    companion object {
        fun fromJson(json: JSONObject): AmbientLanFrame? {
            val bufferId = runCatching { EphemeralBufferId.valueOf(json.getString("buffer_id")) }.getOrNull() ?: return null
            val kind = runCatching { AmbientLanPayloadKind.valueOf(json.optString("kind", AmbientLanPayloadKind.BYTES.name)) }.getOrNull()
                ?: return null
            val payload = runCatching { Base64.decode(json.optString("payload_base64"), Base64.DEFAULT) }.getOrNull() ?: return null
            return AmbientLanFrame(
                id = json.optString("id", "${bufferId.name}:${json.optLong("timestamp_millis")}"),
                bufferId = bufferId,
                kind = kind,
                timestampMillis = json.optLong("timestamp_millis", System.currentTimeMillis()),
                payload = payload,
                metadata = json.optJSONObject("metadata").toStringMap()
            )
        }
    }
}

data class AmbientLanFrameBatch(
    val protocol: String = AmbientLanDiscoveryContract.PROTOCOL_VERSION,
    val deviceId: String,
    val frames: List<AmbientLanFrame>
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("protocol", protocol)
            .put("device_id", deviceId)
            .put("frames", JSONArray(frames.map { it.toJson() }))

    companion object {
        fun fromJson(json: JSONObject): AmbientLanFrameBatch =
            AmbientLanFrameBatch(
                protocol = json.optString("protocol", AmbientLanDiscoveryContract.PROTOCOL_VERSION),
                deviceId = json.optString("device_id", "unknown"),
                frames =
                    json
                        .optJSONArray("frames")
                        .toJsonObjects()
                        .mapNotNull(AmbientLanFrame::fromJson)
            )
    }
}

internal fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}

internal fun JSONArray?.toJsonObjects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index) }
}

internal fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) return emptyMap()
    val out = linkedMapOf<String, String>()
    keys().forEach { key -> out[key] = optString(key) }
    return out
}
