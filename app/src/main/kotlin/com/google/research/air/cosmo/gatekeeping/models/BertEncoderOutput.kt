package com.google.research.air.cosmo.gatekeeping.models

data class BertEncoderOutput(
    val pooledEncoding: FloatArray,
    val tokenEncodings: FloatArray,
    val mask: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BertEncoderOutput

        if (!pooledEncoding.contentEquals(other.pooledEncoding)) return false
        if (!tokenEncodings.contentEquals(other.tokenEncodings)) return false
        return mask.contentEquals(other.mask)
    }

    override fun hashCode(): Int {
        var result = pooledEncoding.contentHashCode()
        result = 31 * result + tokenEncodings.contentHashCode()
        result = 31 * result + mask.contentHashCode()
        return result
    }
}
