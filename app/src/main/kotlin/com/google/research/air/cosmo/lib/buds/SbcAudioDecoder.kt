package com.google.research.air.cosmo.lib.buds

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton

interface SbcAudioDecoder {
    fun decode(bytes: ByteArray): ByteArray

    fun reset()
}

@Singleton
class ReflectiveSbcAudioDecoder
@Inject
constructor(
    @ApplicationContext private val context: Context
) : SbcAudioDecoder {
    private val delegate: Any? = createDelegate()
    private val decodeMethod: Method? = findDecodeMethod(delegate)
    private val resetMethod: Method? =
        delegate
            ?.javaClass
            ?.methods
            ?.firstOrNull { it.name in RESET_METHOD_NAMES && it.parameterCount == 0 }

    override fun decode(bytes: ByteArray): ByteArray {
        val target = delegate
        val method = decodeMethod
        if (target == null || method == null) {
            return bytes
        }
        return runCatching {
            when (method.parameterCount) {
                1 -> method.invoke(target, bytes) as? ByteArray
                else -> null
            } ?: bytes
        }.getOrDefault(bytes)
    }

    override fun reset() {
        runCatching { resetMethod?.invoke(delegate) }
    }

    private fun createDelegate(): Any? =
        SBC_DECODER_CLASS_NAMES.firstNotNullOfOrNull { className ->
            runCatching {
                val clazz = Class.forName(className)
                val noArg = clazz.declaredConstructors.firstOrNull { it.parameterCount == 0 }
                if (noArg != null) {
                    noArg.isAccessible = true
                    return@runCatching noArg.newInstance()
                }
                val decoderClass = Class.forName("$className\$Decoder")
                val decoderConstructor = decoderClass.declaredConstructors.firstOrNull { it.parameterTypes.contentEquals(arrayOf(Context::class.java)) }
                decoderConstructor?.isAccessible = true
                val decoder = decoderConstructor?.newInstance(context) ?: return@runCatching null
                val constructor = clazz.declaredConstructors.firstOrNull { it.parameterTypes.contentEquals(arrayOf(decoderClass)) }
                constructor?.isAccessible = true
                constructor?.newInstance(decoder)
            }.getOrNull()
        }

    private fun findDecodeMethod(delegate: Any?): Method? =
        delegate
            ?.javaClass
            ?.methods
            ?.firstOrNull { method ->
                method.name in DECODE_METHOD_NAMES &&
                    method.parameterTypes.contentEquals(arrayOf(ByteArray::class.java)) &&
                    method.returnType == ByteArray::class.java
            }

    companion object {
        private val SBC_DECODER_CLASS_NAMES =
            listOf(
                "com.google.android.apps.search.assistant.surfaces.bisto.interactor.sbcdecoder.monodecoder.JniSbcDecoderImpl",
                "com.google.research.air.cosmo.lib.buds.JniSbcDecoderImpl"
            )
        private val DECODE_METHOD_NAMES = setOf("decodeSbcAudio")
        private val RESET_METHOD_NAMES = setOf("finishDecodingIfCurrentlyRunning")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SbcAudioDecoderModule {
    @Binds abstract fun bindSbcAudioDecoder(impl: ReflectiveSbcAudioDecoder): SbcAudioDecoder
}
