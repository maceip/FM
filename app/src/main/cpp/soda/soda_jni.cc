// Binary-compatible replacement for libsoda_dev_jni.so.
//
// Preserves every Java_… symbol the recovered Soda Java/Kotlin surface
// expects so SodaJniLoader (which probes ReLinker for "soda_dev_jni" among
// ~25 candidate names) finds us. PCM frames flow into a ring buffer and
// the lifecycle (Construct/Init/StartCapture/Add*Audio/Start/Stop/Delete)
// drives Soda.handleStart/handleStop/handleShutdown upcalls correctly.
// Greco / SpeakerID / S3 transport / diarization paths are deliberately
// short-circuited — they return empty status and never crash the JVM.
// No ASR backend is bound here.

#include <jni.h>

#include <android/log.h>

#include <atomic>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

namespace {

constexpr char kLogTag[] = "SodaDevJni";

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, kLogTag, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, kLogTag, __VA_ARGS__)

// ---------------------------------------------------------------------------
// PCM16 ring buffer.
// ---------------------------------------------------------------------------

class Pcm16Ring {
 public:
  void Reset(size_t capacity_samples) {
    std::lock_guard<std::mutex> lock(mutex_);
    buffer_.assign(capacity_samples, 0);
    head_ = 0;
    size_ = 0;
  }

  // Writes interleaved PCM16 mono samples. If full, oldest samples are
  // dropped. Returns the number of samples written (= count).
  size_t Write(const int16_t* samples, size_t count) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (buffer_.empty()) {
      return 0;
    }
    for (size_t i = 0; i < count; ++i) {
      buffer_[(head_ + size_) % buffer_.size()] = samples[i];
      if (size_ < buffer_.size()) {
        ++size_;
      } else {
        head_ = (head_ + 1) % buffer_.size();
      }
    }
    return count;
  }

  // Drains everything currently held into a contiguous f32 vector,
  // normalized to [-1, 1].
  std::vector<float> DrainAsFloat() {
    std::lock_guard<std::mutex> lock(mutex_);
    std::vector<float> out;
    out.reserve(size_);
    for (size_t i = 0; i < size_; ++i) {
      const int16_t s = buffer_[(head_ + i) % buffer_.size()];
      out.push_back(static_cast<float>(s) / 32768.0f);
    }
    head_ = 0;
    size_ = 0;
    return out;
  }

  size_t SamplesAvailable() {
    std::lock_guard<std::mutex> lock(mutex_);
    return size_;
  }

 private:
  std::vector<int16_t> buffer_;
  size_t head_ = 0;
  size_t size_ = 0;
  std::mutex mutex_;
};

// ---------------------------------------------------------------------------
// JavaVM cache — needed for upcalls into Soda.handleStart/handleStop/etc
// from threads that did not enter through JNI.
// ---------------------------------------------------------------------------

JavaVM* g_jvm = nullptr;

class ScopedEnv {
 public:
  ScopedEnv() {
    if (g_jvm == nullptr) return;
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6) ==
        JNI_OK) {
      return;
    }
    if (g_jvm->AttachCurrentThread(&env_, nullptr) == JNI_OK) {
      detach_ = true;
    } else {
      env_ = nullptr;
    }
  }

  ~ScopedEnv() {
    if (detach_ && g_jvm != nullptr) {
      g_jvm->DetachCurrentThread();
    }
  }

  JNIEnv* get() const { return env_; }

 private:
  JNIEnv* env_ = nullptr;
  bool detach_ = false;
};

// ---------------------------------------------------------------------------
// Soda native handle.
// ---------------------------------------------------------------------------

struct SodaSharedResources {
  std::atomic<int> refs{1};
};

struct SodaHandle {
  // Java-side weak reference to the Soda instance so we can call back its
  // protected handleStart/handleStop/handleSodaEvent/handleShutdown methods.
  jweak soda_weak = nullptr;
  jmethodID mid_handle_start = nullptr;
  jmethodID mid_handle_stop = nullptr;
  jmethodID mid_handle_shutdown = nullptr;
  jmethodID mid_handle_soda_event = nullptr;

  // Provider / transport / APA logger — globalref, lifetime owned by handle.
  jobject data_provider = nullptr;
  jobject transport_factory = nullptr;
  jobject apa_logger = nullptr;

  // Direct ByteBuffer addresses cached by AudioInput.cacheDirectBufferAddress.
  uint8_t* audio_buffer_addr = nullptr;
  int64_t* timestamp_buffer_addr = nullptr;
  jobject audio_buffer_global = nullptr;
  jobject timestamp_buffer_global = nullptr;

  // Audio capture state.
  Pcm16Ring ring;
  std::atomic<int> sample_rate_hz{16000};
  std::atomic<int> channels{1};
  std::atomic<bool> capturing{false};
  std::atomic<bool> asr_active{false};

  std::shared_ptr<SodaSharedResources> shared;
  std::mutex mutex;
};

// ---------------------------------------------------------------------------
// Java upcalls.
// ---------------------------------------------------------------------------

void EmitHandleStart(SodaHandle* h) {
  if (h == nullptr || h->soda_weak == nullptr) return;
  ScopedEnv env;
  if (env.get() == nullptr) return;
  jobject local = env.get()->NewLocalRef(h->soda_weak);
  if (local == nullptr) return;
  if (h->mid_handle_start != nullptr) {
    env.get()->CallVoidMethod(local, h->mid_handle_start);
    if (env.get()->ExceptionCheck()) env.get()->ExceptionClear();
  }
  env.get()->DeleteLocalRef(local);
}

void EmitHandleStop(SodaHandle* h, int reason_ordinal) {
  if (h == nullptr || h->soda_weak == nullptr) return;
  ScopedEnv env;
  if (env.get() == nullptr) return;
  jobject local = env.get()->NewLocalRef(h->soda_weak);
  if (local == nullptr) return;
  if (h->mid_handle_stop != nullptr) {
    env.get()->CallVoidMethod(local, h->mid_handle_stop,
                              static_cast<jint>(reason_ordinal));
    if (env.get()->ExceptionCheck()) env.get()->ExceptionClear();
  }
  env.get()->DeleteLocalRef(local);
}

void EmitHandleShutdown(SodaHandle* h) {
  if (h == nullptr || h->soda_weak == nullptr) return;
  ScopedEnv env;
  if (env.get() == nullptr) return;
  jobject local = env.get()->NewLocalRef(h->soda_weak);
  if (local == nullptr) return;
  if (h->mid_handle_shutdown != nullptr) {
    env.get()->CallVoidMethod(local, h->mid_handle_shutdown);
    if (env.get()->ExceptionCheck()) env.get()->ExceptionClear();
  }
  env.get()->DeleteLocalRef(local);
}

// ---------------------------------------------------------------------------
// Helpers.
// ---------------------------------------------------------------------------

jbyteArray EmptyByteArray(JNIEnv* env) {
  return env->NewByteArray(0);
}

void ResolveSodaCallbackMethods(JNIEnv* env, SodaHandle* h, jobject soda) {
  jclass cls = env->GetObjectClass(soda);
  if (cls == nullptr) return;
  h->mid_handle_start = env->GetMethodID(cls, "handleStart", "()V");
  if (env->ExceptionCheck()) env->ExceptionClear();
  h->mid_handle_stop = env->GetMethodID(cls, "handleStop", "(I)V");
  if (env->ExceptionCheck()) env->ExceptionClear();
  h->mid_handle_shutdown = env->GetMethodID(cls, "handleShutdown", "()V");
  if (env->ExceptionCheck()) env->ExceptionClear();
  h->mid_handle_soda_event = env->GetMethodID(cls, "handleSodaEvent", "([B)V");
  if (env->ExceptionCheck()) env->ExceptionClear();
  env->DeleteLocalRef(cls);
}

void ReleaseGlobals(JNIEnv* env, SodaHandle* h) {
  if (h->soda_weak != nullptr) {
    env->DeleteWeakGlobalRef(h->soda_weak);
    h->soda_weak = nullptr;
  }
  if (h->data_provider != nullptr) {
    env->DeleteGlobalRef(h->data_provider);
    h->data_provider = nullptr;
  }
  if (h->transport_factory != nullptr) {
    env->DeleteGlobalRef(h->transport_factory);
    h->transport_factory = nullptr;
  }
  if (h->apa_logger != nullptr) {
    env->DeleteGlobalRef(h->apa_logger);
    h->apa_logger = nullptr;
  }
  if (h->audio_buffer_global != nullptr) {
    env->DeleteGlobalRef(h->audio_buffer_global);
    h->audio_buffer_global = nullptr;
    h->audio_buffer_addr = nullptr;
  }
  if (h->timestamp_buffer_global != nullptr) {
    env->DeleteGlobalRef(h->timestamp_buffer_global);
    h->timestamp_buffer_global = nullptr;
    h->timestamp_buffer_addr = nullptr;
  }
}

}  // namespace

// ---------------------------------------------------------------------------
// JNI_OnLoad — cache the JavaVM for cross-thread upcalls.
// ---------------------------------------------------------------------------

extern "C" __attribute__((visibility("default"))) jint JNI_OnLoad(JavaVM* vm,
                                                                  void*) {
  g_jvm = vm;
  return JNI_VERSION_1_6;
}

extern "C" __attribute__((visibility("default"))) void JNI_OnUnload(JavaVM*,
                                                                    void*) {
  g_jvm = nullptr;
}

// ---------------------------------------------------------------------------
// Soda — instance natives.
// ---------------------------------------------------------------------------

#define EXPORT extern "C" __attribute__((visibility("default")))

EXPORT JNIEXPORT jlong JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeConstruct(
    JNIEnv* env, jobject thiz, jlong shared_resources) {
  auto* h = new SodaHandle();
  h->soda_weak = env->NewWeakGlobalRef(thiz);
  ResolveSodaCallbackMethods(env, h, thiz);
  if (shared_resources != 0) {
    auto* shared = reinterpret_cast<SodaSharedResources*>(shared_resources);
    shared->refs.fetch_add(1);
    h->shared.reset(shared, [](SodaSharedResources* p) {
      if (p->refs.fetch_sub(1) == 1) delete p;
    });
  }
  h->ring.Reset(16000 * 30);  // 30 s of mono 16 kHz PCM
  return reinterpret_cast<jlong>(h);
}

EXPORT JNIEXPORT jbyteArray JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeInit(
    JNIEnv* env, jobject thiz, jlong handle, jbyteArray /*config_bytes*/) {
  // ConfigResult proto with default fields == OK is an empty byte array on
  // the wire. Soda.smali tolerates an empty result.
  if (handle == 0) {
    LOGE("nativeInit: null handle");
    return EmptyByteArray(env);
  }
  return EmptyByteArray(env);
}

EXPORT JNIEXPORT jbyteArray JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeStartCapture(
    JNIEnv* env, jobject thiz, jlong handle, jbyteArray /*config_bytes*/) {
  auto* h = reinterpret_cast<SodaHandle*>(handle);
  if (h == nullptr) return EmptyByteArray(env);
  h->capturing.store(true);
  h->ring.Reset(16000 * 30);
  EmitHandleStart(h);
  return EmptyByteArray(env);
}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeStartAsr(
    JNIEnv* env, jobject thiz, jlong handle, jint /*trigger_type*/) {
  auto* h = reinterpret_cast<SodaHandle*>(handle);
  if (h == nullptr) return;
  h->asr_active.store(true);
}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeCancelAsr(
    JNIEnv* env, jobject thiz, jlong handle) {
  auto* h = reinterpret_cast<SodaHandle*>(handle);
  if (h == nullptr) return;
  h->asr_active.store(false);
}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeStopCapture(
    JNIEnv* env, jobject thiz, jlong handle) {
  auto* h = reinterpret_cast<SodaHandle*>(handle);
  if (h == nullptr) return;
  h->asr_active.store(false);
  h->ring.DrainAsFloat();  // discard buffered audio; no backend bound
  h->capturing.store(false);
  EmitHandleStop(h, /*reason ordinal*/ 0);
}

// audio[1]: handle, ByteBuffer, count
EXPORT JNIEXPORT jboolean JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeAddAudio(
    JNIEnv* env, jobject thiz, jlong handle, jobject buffer, jlong count) {
  auto* h = reinterpret_cast<SodaHandle*>(handle);
  if (h == nullptr || buffer == nullptr || count <= 0) return JNI_FALSE;
  auto* addr = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
  if (addr == nullptr) {
    if (h->audio_buffer_addr != nullptr) {
      addr = h->audio_buffer_addr;
    } else {
      LOGE("nativeAddAudio: ByteBuffer is not direct and no cached address");
      return JNI_FALSE;
    }
  }
  const auto* samples = reinterpret_cast<const int16_t*>(addr);
  const size_t sample_count = static_cast<size_t>(count) / sizeof(int16_t);
  h->ring.Write(samples, sample_count);
  return JNI_TRUE;
}

EXPORT JNIEXPORT jboolean JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeAddTimestampedAudio(
    JNIEnv* env, jobject thiz, jlong handle, jobject audio_buffer,
    jlong audio_count, jobject /*timestamp_buffer*/, jlong /*timestamp_count*/) {
  return Java_com_google_android_libraries_assistant_soda_Soda_nativeAddAudio(
      env, thiz, handle, audio_buffer, audio_count);
}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeAddTimestampedLoopbackAudio(
    JNIEnv* env, jobject thiz, jlong handle, jobject /*audio_buffer*/,
    jlong /*audio_count*/, jobject /*timestamp_buffer*/,
    jlong /*timestamp_count*/) {
  // Loopback audio is for AEC reference; clean track ignores it.
  (void)handle;
}

EXPORT JNIEXPORT jbyteArray JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativePushAudio(
    JNIEnv* env, jobject thiz, jlong handle, jint /*source*/, jint /*error*/,
    jobject buffer, jlong duration_nanos, jint /*flags*/) {
  auto* h = reinterpret_cast<SodaHandle*>(handle);
  if (h == nullptr || buffer == nullptr) return EmptyByteArray(env);
  auto* addr = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
  if (addr == nullptr) return EmptyByteArray(env);
  // duration_nanos is the timestamp delta between adjacent buffers in nanos;
  // count of bytes is implied by the buffer's remaining(). JNI does not
  // expose remaining() directly, so we use GetDirectBufferCapacity.
  const jlong cap = env->GetDirectBufferCapacity(buffer);
  if (cap > 0) {
    const auto* samples = reinterpret_cast<const int16_t*>(addr);
    h->ring.Write(samples, static_cast<size_t>(cap) / sizeof(int16_t));
  }
  (void)duration_nanos;
  return EmptyByteArray(env);
}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeUpdateContext(
    JNIEnv*, jobject, jlong, jbyteArray) {}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeUpdateRecognitionContext(
    JNIEnv*, jobject, jlong, jbyteArray) {}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeUpdateRuntime(
    JNIEnv*, jobject, jlong, jbyteArray) {}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeExecuteTasks(
    JNIEnv*, jobject, jlong, jbyteArray) {}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeLogEvents(
    JNIEnv*, jobject, jlong, jbyteArray) {}

EXPORT JNIEXPORT jboolean JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeRequiresReinitialization(
    JNIEnv*, jobject, jlong, jbyteArray) {
  return JNI_FALSE;
}

EXPORT JNIEXPORT jbyteArray JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeEnrollForVoiceMatch(
    JNIEnv* env, jobject, jlong, jbyteArray) {
  return EmptyByteArray(env);
}

EXPORT JNIEXPORT jbyteArray JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeGetSpeakerIdEnrollmentInfo(
    JNIEnv* env, jobject, jlong) {
  return EmptyByteArray(env);
}

EXPORT JNIEXPORT jboolean JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeHasPendingGrecoTurn(
    JNIEnv*, jobject, jlong) {
  return JNI_FALSE;
}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeDoGrecoTurn(
    JNIEnv*, jobject, jlong, jboolean) {}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeCollectDebugInfo(
    JNIEnv*, jobject, jlong, jboolean) {}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeSetApaLogger(
    JNIEnv* env, jobject, jlong handle, jobject apa_logger) {
  auto* h = reinterpret_cast<SodaHandle*>(handle);
  if (h == nullptr) return;
  if (h->apa_logger != nullptr) env->DeleteGlobalRef(h->apa_logger);
  h->apa_logger = apa_logger != nullptr ? env->NewGlobalRef(apa_logger)
                                        : nullptr;
}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeSetDataProvider(
    JNIEnv* env, jobject, jlong handle, jobject provider) {
  auto* h = reinterpret_cast<SodaHandle*>(handle);
  if (h == nullptr) return;
  if (h->data_provider != nullptr) env->DeleteGlobalRef(h->data_provider);
  h->data_provider = provider != nullptr ? env->NewGlobalRef(provider)
                                         : nullptr;
}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeSetTransportFactory(
    JNIEnv* env, jobject, jlong handle, jobject factory) {
  auto* h = reinterpret_cast<SodaHandle*>(handle);
  if (h == nullptr) return;
  if (h->transport_factory != nullptr) {
    env->DeleteGlobalRef(h->transport_factory);
  }
  h->transport_factory = factory != nullptr ? env->NewGlobalRef(factory)
                                            : nullptr;
}

EXPORT JNIEXPORT jboolean JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeDeleteDiarizationProcessor(
    JNIEnv*, jobject, jlong) {
  return JNI_TRUE;
}

EXPORT JNIEXPORT jboolean JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeDeleteSharedResourcesIfDiarizationProcessorAbsent(
    JNIEnv*, jobject, jlong) {
  return JNI_TRUE;
}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeDelete(
    JNIEnv* env, jobject, jlong handle) {
  auto* h = reinterpret_cast<SodaHandle*>(handle);
  if (h == nullptr) return;
  if (h->capturing.exchange(false)) {
    EmitHandleShutdown(h);
  }
  ReleaseGlobals(env, h);
  delete h;
}

// ---------------------------------------------------------------------------
// Soda — static natives.
// ---------------------------------------------------------------------------

EXPORT JNIEXPORT jlong JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeCreateSharedResources(
    JNIEnv*, jclass, jobject) {
  return reinterpret_cast<jlong>(new SodaSharedResources());
}

EXPORT JNIEXPORT jboolean JNICALL
Java_com_google_android_libraries_assistant_soda_Soda_nativeDeleteSharedResources(
    JNIEnv*, jclass, jobject, jlong shared) {
  if (shared == 0) return JNI_FALSE;
  auto* s = reinterpret_cast<SodaSharedResources*>(shared);
  if (s->refs.fetch_sub(1) == 1) {
    delete s;
  }
  return JNI_TRUE;
}

// ---------------------------------------------------------------------------
// AudioInput.
// ---------------------------------------------------------------------------

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_AudioInput_nativeCacheDirectBufferAddress(
    JNIEnv* env, jobject, jlong handle, jobject audio_buffer,
    jobject timestamp_buffer) {
  auto* h = reinterpret_cast<SodaHandle*>(handle);
  if (h == nullptr) return;

  if (h->audio_buffer_global != nullptr) {
    env->DeleteGlobalRef(h->audio_buffer_global);
    h->audio_buffer_global = nullptr;
    h->audio_buffer_addr = nullptr;
  }
  if (h->timestamp_buffer_global != nullptr) {
    env->DeleteGlobalRef(h->timestamp_buffer_global);
    h->timestamp_buffer_global = nullptr;
    h->timestamp_buffer_addr = nullptr;
  }

  if (audio_buffer != nullptr) {
    void* addr = env->GetDirectBufferAddress(audio_buffer);
    if (addr != nullptr) {
      h->audio_buffer_global = env->NewGlobalRef(audio_buffer);
      h->audio_buffer_addr = static_cast<uint8_t*>(addr);
    } else {
      LOGE("AudioInput.cacheDirectBufferAddress: audio buffer is not direct");
    }
  }
  if (timestamp_buffer != nullptr) {
    void* addr = env->GetDirectBufferAddress(timestamp_buffer);
    if (addr != nullptr) {
      h->timestamp_buffer_global = env->NewGlobalRef(timestamp_buffer);
      h->timestamp_buffer_addr = static_cast<int64_t*>(addr);
    }
  }
}

// ---------------------------------------------------------------------------
// SodaBenchmark / SodaDataProviderJni / SodaTransportSession /
// PhoneticDistance / SodaEnrollmentManagerImpl —
// signatures recovered from the libsoda_dev_jni.so symbol table.
// Bodies are intentionally short-circuit stubs.
// ---------------------------------------------------------------------------

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_benchmark_SodaBenchmark_nativeRunBenchmarking(
    JNIEnv*, jclass) {}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_data_SodaDataProviderJni_nativeHandleDataResponse(
    JNIEnv*, jobject, jlong, jlong, jbyteArray) {}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_data_SodaDataProviderJni_nativeHandleEarlySodaEvent(
    JNIEnv*, jobject, jlong, jlong, jbyteArray) {}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_s3client_SodaTransportSession_nativeHandleResponse(
    JNIEnv*, jobject, jlong, jbyteArray) {}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_s3client_SodaTransportSession_nativeHandleError(
    JNIEnv*, jobject, jlong, jstring) {}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_s3client_SodaTransportSession_nativeHandleState(
    JNIEnv*, jobject, jlong, jint) {}

EXPORT JNIEXPORT jlong JNICALL
Java_com_google_android_libraries_assistant_soda_jdq_PhoneticDistance_nativeCreate(
    JNIEnv*, jobject) {
  return 0;
}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_jdq_PhoneticDistance_nativeInit(
    JNIEnv*, jobject, jlong) {}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_jdq_PhoneticDistance_nativeUpdate(
    JNIEnv*, jobject, jlong) {}

EXPORT JNIEXPORT jbyteArray JNICALL
Java_com_google_android_libraries_assistant_soda_jdq_PhoneticDistance_nativeLookup(
    JNIEnv* env, jobject, jlong, jstring) {
  return EmptyByteArray(env);
}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_soda_jdq_PhoneticDistance_nativeDelete(
    JNIEnv*, jobject, jlong) {}

EXPORT JNIEXPORT jlong JNICALL
Java_com_google_android_libraries_assistant_speakerid_SodaEnrollmentManagerImpl_nativeConstruct(
    JNIEnv*, jobject) {
  return 0;
}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_speakerid_SodaEnrollmentManagerImpl_nativeDelete(
    JNIEnv*, jobject, jlong) {}

EXPORT JNIEXPORT void JNICALL
Java_com_google_android_libraries_assistant_speakerid_SodaEnrollmentManagerImpl_nativeProcessRequest(
    JNIEnv*, jobject, jlong, jbyteArray) {}

EXPORT JNIEXPORT jbyteArray JNICALL
Java_com_google_android_libraries_assistant_speakerid_SodaEnrollmentManagerImpl_nativeGetSpeakerInfo(
    JNIEnv* env, jobject, jlong) {
  return EmptyByteArray(env);
}

EXPORT JNIEXPORT jbyteArray JNICALL
Java_com_google_android_libraries_assistant_speakerid_SodaEnrollmentManagerImpl_nativeGetVoiceMatchModels(
    JNIEnv* env, jobject, jlong) {
  return EmptyByteArray(env);
}

EXPORT JNIEXPORT jbyteArray JNICALL
Java_com_google_android_libraries_assistant_speakerid_SodaEnrollmentManagerImpl_nativeGetEnrollmentAudio(
    JNIEnv* env, jobject, jlong) {
  return EmptyByteArray(env);
}

EXPORT JNIEXPORT jbyteArray JNICALL
Java_com_google_android_libraries_assistant_speakerid_SodaEnrollmentManagerImpl_nativeGetEnrollmentAudioInfo(
    JNIEnv* env, jobject, jlong) {
  return EmptyByteArray(env);
}
