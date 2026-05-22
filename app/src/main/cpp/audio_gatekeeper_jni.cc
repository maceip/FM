#include <jni.h>

#include <android/log.h>
#include <dlfcn.h>

#include "litert_minimal_c_api.h"

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <memory>
#include <mutex>
#include <numeric>
#include <optional>
#include <string>
#include <utility>
#include <vector>

namespace {

constexpr char kLogTag[] = "AudioGatekeeperJni";
constexpr bool kEnableGoogleTensorDispatchRuntime = true;

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, kLogTag, __VA_ARGS__)

void LogGoogleTensorLibraryLoad(const char* library_name) {
  dlerror();
  void* handle = dlopen(library_name, RTLD_NOW | RTLD_GLOBAL);
  if (handle == nullptr) {
    LOGW("Google Tensor dependency dlopen failed: %s: %s", library_name, dlerror());
    return;
  }
  LOGW("Google Tensor dependency dlopen ok: %s", library_name);
}

bool CanInitializeGoogleTensorSouthBound() {
  if (!kEnableGoogleTensorDispatchRuntime) {
    LOGW("Google Tensor LiteRT dispatch disabled pending a non-aborting runtime package");
    return false;
  }
  LogGoogleTensorLibraryLoad("libedgetpu_util.so");
  LogGoogleTensorLibraryLoad("com.google.edgetpu_vendor_service-V2-ndk.so");
  LogGoogleTensorLibraryLoad("libedgetpu_client.google.so");
  LogGoogleTensorLibraryLoad("libedgetpu_tachyon.google.so");

  dlerror();
  void* south_bound_lib = dlopen("libedgetpu_litert.so", RTLD_NOW | RTLD_GLOBAL);
  if (south_bound_lib == nullptr) {
    LOGW("Google Tensor SouthBound dlopen failed: %s", dlerror());
    return false;
  }

  dlerror();
  void* thr_initialize = dlsym(south_bound_lib, "thrInitialize");
  const char* dlsym_error = dlerror();
  if (thr_initialize == nullptr || dlsym_error != nullptr) {
    LOGW("Google Tensor SouthBound thrInitialize lookup failed: %s",
         dlsym_error != nullptr ? dlsym_error : "symbol unavailable");
    return false;
  }
  using ThrGetStringFn = const char* (*)();
  dlerror();
  auto thr_get_vendor_api_version =
      reinterpret_cast<ThrGetStringFn>(dlsym(south_bound_lib, "thrGetVendorApiVersion"));
  const char* vendor_api_version =
      thr_get_vendor_api_version == nullptr ? "(unavailable)" : thr_get_vendor_api_version();
  dlerror();
  auto thr_get_vendor_id =
      reinterpret_cast<ThrGetStringFn>(dlsym(south_bound_lib, "thrGetVendorId"));
  const char* vendor_id = thr_get_vendor_id == nullptr ? "(unavailable)" : thr_get_vendor_id();
  LOGW("Google Tensor SouthBound available for audio LiteRT dispatch: api=%s vendor=%s",
       vendor_api_version == nullptr ? "(null)" : vendor_api_version,
       vendor_id == nullptr ? "(null)" : vendor_id);
  return true;
}

std::optional<std::vector<uint8_t>> CopyByteArray(JNIEnv* env, jbyteArray array) {
  if (array == nullptr) {
    return std::nullopt;
  }
  const jsize length = env->GetArrayLength(array);
  if (length <= 0) {
    return std::vector<uint8_t>{};
  }
  std::vector<uint8_t> bytes(static_cast<size_t>(length));
  env->GetByteArrayRegion(array, 0, length, reinterpret_cast<jbyte*>(bytes.data()));
  if (env->ExceptionCheck()) {
    return std::nullopt;
  }
  return bytes;
}

std::string CopyString(JNIEnv* env, jstring value) {
  if (value == nullptr) {
    return {};
  }
  const char* chars = env->GetStringUTFChars(value, nullptr);
  if (chars == nullptr) {
    return {};
  }
  std::string copy(chars);
  env->ReleaseStringUTFChars(value, chars);
  return copy;
}

std::optional<std::vector<int32_t>> CopyIntArray(JNIEnv* env, jintArray array) {
  if (array == nullptr) {
    return std::vector<int32_t>{};
  }
  const jsize length = env->GetArrayLength(array);
  if (length < 0) {
    return std::nullopt;
  }
  std::vector<int32_t> values(static_cast<size_t>(length));
  if (length > 0) {
    env->GetIntArrayRegion(array, 0, length, reinterpret_cast<jint*>(values.data()));
    if (env->ExceptionCheck()) {
      return std::nullopt;
    }
  }
  return values;
}

size_t ElementCount(const LiteRtRankedTensorType& type) {
  if (type.layout.rank == 0 || type.layout.rank > 8) {
    return 0;
  }
  size_t count = 1;
  for (unsigned int i = 0; i < type.layout.rank; ++i) {
    const int32_t dimension = type.layout.dimensions[i];
    if (dimension <= 0) {
      return 0;
    }
    count *= static_cast<size_t>(dimension);
  }
  return count;
}

std::string DescribeShape(const LiteRtRankedTensorType& type) {
  std::string out = "[";
  for (unsigned int i = 0; i < type.layout.rank; ++i) {
    if (i > 0) {
      out += ",";
    }
    out += std::to_string(type.layout.dimensions[i]);
  }
  out += "]";
  return out;
}

std::string DescribeShapeVector(const std::vector<int32_t>& shape) {
  std::string out = "[";
  for (size_t i = 0; i < shape.size(); ++i) {
    if (i > 0) {
      out += ",";
    }
    out += std::to_string(shape[i]);
  }
  out += "]";
  return out;
}

bool ShapeEquals(const LiteRtRankedTensorType& actual, const std::vector<int32_t>& expected) {
  if (expected.empty()) {
    return true;
  }
  if (actual.layout.rank != expected.size()) {
    return false;
  }
  for (unsigned int i = 0; i < actual.layout.rank; ++i) {
    if (actual.layout.dimensions[i] != expected[i]) {
      return false;
    }
  }
  return true;
}

class LiteRtAudioModel {
 public:
  bool Initialize(const std::vector<uint8_t>& model_bytes, bool use_npu,
                  std::vector<int32_t> expected_input_shape,
                  std::vector<int32_t> expected_output_shape,
                  std::string dispatch_library_dir) {
    model_bytes_ = model_bytes;
    use_npu_ = use_npu;
    expected_input_shape_ = std::move(expected_input_shape);
    expected_output_shape_ = std::move(expected_output_shape);
    dispatch_library_dir_ = std::move(dispatch_library_dir);
    if (!LoadLiteRtSymbols()) {
      return false;
    }
    if (!CreateEnvironment() ||
        !Check(api_.CreateModelFromBuffer(model_bytes_.data(), model_bytes_.size(), &model_),
               "LiteRtCreateModelFromBuffer") ||
        !ValidateModelContract() ||
        !Check(api_.CreateOptions(&options_), "LiteRtCreateOptions")) {
      Destroy();
      return false;
    }
	    const LiteRtHwAcceleratorSet accelerators =
	        use_npu_ ? kLiteRtHwAcceleratorNpu : kLiteRtHwAcceleratorCpu;
	    if (!Check(api_.SetOptionsHardwareAccelerators(options_, accelerators),
	               "LiteRtSetOptionsHardwareAccelerators") ||
	        !Check(api_.CreateCompiledModel(environment_, model_, options_, &compiled_model_),
	               "LiteRtCreateCompiledModel") ||
	        !CreateTensorBuffers()) {
	      Destroy();
	      return false;
	    }
	    return true;
	  }

  bool Run(JNIEnv* env, jobject input_buffer, jobject output_buffer) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (compiled_model_ == nullptr) {
      LOGE("LiteRT audio model is not initialized");
      return false;
    }
    float* input = reinterpret_cast<float*>(env->GetDirectBufferAddress(input_buffer));
    float* output = reinterpret_cast<float*>(env->GetDirectBufferAddress(output_buffer));
    // GetDirectBufferCapacity returns the buffer's element count (per the JNI spec). For the
    // FloatBuffers Kotlin passes us, that's already the float count — no need to divide by
    // sizeof(float) again.
    const jlong input_capacity_elements = env->GetDirectBufferCapacity(input_buffer);
    const jlong output_capacity_elements = env->GetDirectBufferCapacity(output_buffer);
    if (input == nullptr || output == nullptr || input_capacity_elements < 0 || output_capacity_elements < 0) {
      LOGE("Audio interpreter requires direct FloatBuffer inputs and outputs");
      return false;
    }
    const size_t input_floats = static_cast<size_t>(input_capacity_elements);
    const size_t output_floats = static_cast<size_t>(output_capacity_elements);
    if (input_floats < input_element_count_ || output_floats < output_element_count_) {
      LOGE("Audio tensor buffer too small: input=%zu/%zu output=%zu/%zu",
           input_floats, input_element_count_, output_floats, output_element_count_);
      return false;
    }

	    if (input_buffer_ == nullptr || output_buffer_ == nullptr) {
	      LOGE("LiteRT audio tensor buffers are not initialized");
	      return false;
	    }

	    void* input_addr = nullptr;
	    if (!Check(api_.LockTensorBuffer(input_buffer_, &input_addr,
	                                     kLiteRtTensorBufferLockModeWrite),
	               "LiteRtLockTensorBuffer(input)") ||
	        input_addr == nullptr) {
	      return false;
	    }
	    std::memcpy(input_addr, input, input_element_count_ * sizeof(float));
	    if (!Check(api_.UnlockTensorBuffer(input_buffer_),
	               "LiteRtUnlockTensorBuffer(input)")) {
	      return false;
	    }

	    const bool ran =
	        Check(api_.RunCompiledModel(compiled_model_, 0, 1, &input_buffer_, 1,
	                                    &output_buffer_),
	              "LiteRtRunCompiledModel(audio)");
	    if (!ran) {
	      return false;
	    }

	    void* output_addr = nullptr;
	    if (!Check(api_.LockTensorBuffer(output_buffer_, &output_addr,
	                                     kLiteRtTensorBufferLockModeRead),
	               "LiteRtLockTensorBuffer(output)") ||
	        output_addr == nullptr) {
	      return false;
	    }
	    std::memcpy(output, output_addr, output_element_count_ * sizeof(float));
	    return Check(api_.UnlockTensorBuffer(output_buffer_),
	                 "LiteRtUnlockTensorBuffer(output)");
	  }

	  void Destroy() {
	    DestroyBuffer(input_buffer_);
	    input_buffer_ = nullptr;
	    DestroyBuffer(output_buffer_);
	    output_buffer_ = nullptr;
	    if (compiled_model_ != nullptr && api_.DestroyCompiledModel != nullptr) {
	      api_.DestroyCompiledModel(compiled_model_);
	      compiled_model_ = nullptr;
    }
    if (options_ != nullptr && api_.DestroyOptions != nullptr) {
      api_.DestroyOptions(options_);
      options_ = nullptr;
    }
    if (model_ != nullptr && api_.DestroyModel != nullptr) {
      api_.DestroyModel(model_);
      model_ = nullptr;
    }
    if (environment_ != nullptr && api_.DestroyEnvironment != nullptr) {
      api_.DestroyEnvironment(environment_);
      environment_ = nullptr;
    }
  }

 private:
  bool CreateEnvironment() {
    LiteRtEnvOption option{};
    const LiteRtEnvOption* options = nullptr;
    int option_count = 0;
    if (!dispatch_library_dir_.empty()) {
      option.tag = kLiteRtEnvOptionTagDispatchLibraryDir;
      option.value.type = kLiteRtAnyTypeString;
      option.value.str_value = dispatch_library_dir_.c_str();
      options = &option;
      option_count = 1;
      LOGW("Using LiteRT dispatch library dir: %s", dispatch_library_dir_.c_str());
    }
    return Check(api_.CreateEnvironment(option_count, options, &environment_),
                 "LiteRtCreateEnvironment");
  }

  bool LoadLiteRtSymbols() {
    if (litert_lib_ != nullptr) {
      return true;
    }
    litert_lib_ = dlopen("libLiteRt.so", RTLD_NOW | RTLD_LOCAL);
    if (litert_lib_ == nullptr) {
      LOGE("Failed to load libLiteRt.so: %s", dlerror());
      return false;
    }
    return Resolve("LiteRtCreateEnvironment", &api_.CreateEnvironment) &&
           Resolve("LiteRtDestroyEnvironment", &api_.DestroyEnvironment) &&
           Resolve("LiteRtCreateModelFromBuffer", &api_.CreateModelFromBuffer) &&
           Resolve("LiteRtDestroyModel", &api_.DestroyModel) &&
           Resolve("LiteRtGetMainModelSubgraphIndex", &api_.GetMainModelSubgraphIndex) &&
           Resolve("LiteRtGetModelSubgraph", &api_.GetModelSubgraph) &&
           Resolve("LiteRtGetNumSubgraphInputs", &api_.GetNumSubgraphInputs) &&
           Resolve("LiteRtGetSubgraphInput", &api_.GetSubgraphInput) &&
           Resolve("LiteRtGetNumSubgraphOutputs", &api_.GetNumSubgraphOutputs) &&
           Resolve("LiteRtGetSubgraphOutput", &api_.GetSubgraphOutput) &&
           Resolve("LiteRtGetTensorName", &api_.GetTensorName) &&
           Resolve("LiteRtGetTensorTypeId", &api_.GetTensorTypeId) &&
           Resolve("LiteRtGetRankedTensorType", &api_.GetRankedTensorType) &&
           Resolve("LiteRtCreateOptions", &api_.CreateOptions) &&
           Resolve("LiteRtDestroyOptions", &api_.DestroyOptions) &&
           Resolve("LiteRtSetOptionsHardwareAccelerators",
                   &api_.SetOptionsHardwareAccelerators) &&
	          Resolve("LiteRtCreateCompiledModel", &api_.CreateCompiledModel) &&
	          Resolve("LiteRtDestroyCompiledModel", &api_.DestroyCompiledModel) &&
	          Resolve("LiteRtGetCompiledModelInputBufferRequirements",
	                  &api_.GetCompiledModelInputBufferRequirements) &&
	          Resolve("LiteRtGetCompiledModelOutputBufferRequirements",
	                  &api_.GetCompiledModelOutputBufferRequirements) &&
	          Resolve("LiteRtCreateTensorBufferFromHostMemory",
	                  &api_.CreateTensorBufferFromHostMemory) &&
	          Resolve("LiteRtCreateManagedTensorBufferFromRequirements",
	                  &api_.CreateManagedTensorBufferFromRequirements) &&
	          Resolve("LiteRtLockTensorBuffer", &api_.LockTensorBuffer) &&
	          Resolve("LiteRtUnlockTensorBuffer", &api_.UnlockTensorBuffer) &&
	          Resolve("LiteRtDestroyTensorBuffer", &api_.DestroyTensorBuffer) &&
	          Resolve("LiteRtRunCompiledModel", &api_.RunCompiledModel) &&
           ResolveOptional("LiteRtGetStatusString", &api_.GetStatusString) &&
           ResolveOptional("LiteRtGetDefaultLogger", &api_.GetDefaultLogger) &&
           ResolveOptional("LiteRtSetMinLoggerSeverity", &api_.SetMinLoggerSeverity) &&
           EnableVerboseLiteRtLogging();
  }

  template <typename Fn>
  bool Resolve(const char* symbol, Fn* out) {
    dlerror();
    *out = reinterpret_cast<Fn>(dlsym(litert_lib_, symbol));
    const char* error = dlerror();
    if (error != nullptr || *out == nullptr) {
      LOGE("Failed to resolve %s from libLiteRt.so: %s", symbol,
           error == nullptr ? "symbol missing" : error);
      return false;
    }
    return true;
  }

  // Optional symbol resolver: missing symbol is non-fatal (callers must null-check). Used
  // for diagnostic helpers like LiteRtGetStatusString that aren't present in every libLiteRt
  // ABI version.
  template <typename Fn>
  bool ResolveOptional(const char* symbol, Fn* out) {
    dlerror();
    *out = reinterpret_cast<Fn>(dlsym(litert_lib_, symbol));
    if (*out == nullptr) {
      LOGW("Optional symbol %s missing from libLiteRt.so; status messages will be unavailable", symbol);
    }
    return true;
  }

  bool Check(LiteRtStatus status, const char* context) const {
    if (status == kLiteRtStatusOk) {
      return true;
    }
    const char* detail = api_.GetStatusString == nullptr ? nullptr : api_.GetStatusString(status);
    LOGE("%s failed: status=%d%s%s", context, static_cast<int>(status),
         detail == nullptr ? "" : " ", detail == nullptr ? "" : detail);
    return false;
  }

  bool EnableVerboseLiteRtLogging() const {
    if (api_.GetDefaultLogger == nullptr || api_.SetMinLoggerSeverity == nullptr) {
      return true;
    }
    constexpr int8_t kLiteRtLogSeverityDebug = -1;
    LiteRtLogger logger = api_.GetDefaultLogger();
    if (logger == nullptr) {
      LOGW("LiteRT default logger is unavailable; keeping default log severity");
      return true;
    }
    const LiteRtStatus status = api_.SetMinLoggerSeverity(logger, kLiteRtLogSeverityDebug);
    if (status != kLiteRtStatusOk) {
      LOGW("LiteRT verbose logging setup failed: %d", static_cast<int>(status));
    } else {
      LOGW("LiteRT native logger set to debug for Google Tensor dispatch diagnostics");
    }
    return true;
  }

  bool ReadTensorType(LiteRtTensor tensor, const char* role, LiteRtRankedTensorType* out) const {
    LiteRtTensorTypeId type_id = kLiteRtUnrankedTensorType;
    if (!Check(api_.GetTensorTypeId(tensor, &type_id), "LiteRtGetTensorTypeId")) {
      return false;
    }
    if (type_id != kLiteRtRankedTensorType) {
      LOGE("%s tensor is not ranked", role);
      return false;
    }
    LiteRtRankedTensorType type{};
    if (!Check(api_.GetRankedTensorType(tensor, &type), "LiteRtGetRankedTensorType")) {
      return false;
    }
    if (type.element_type != kLiteRtElementTypeFloat32) {
      LOGE("%s tensor must be float32, got type=%d", role, static_cast<int>(type.element_type));
      return false;
    }
    if (ElementCount(type) == 0) {
      LOGE("%s tensor has invalid shape %s", role, DescribeShape(type).c_str());
      return false;
    }
    *out = type;
    return true;
  }

  bool ValidateModelContract() {
    LiteRtParamIndex subgraph_index = 0;
    LiteRtSubgraph subgraph = nullptr;
    LiteRtParamIndex input_count = 0;
    LiteRtParamIndex output_count = 0;
    if (!Check(api_.GetMainModelSubgraphIndex(model_, &subgraph_index),
               "LiteRtGetMainModelSubgraphIndex") ||
        !Check(api_.GetModelSubgraph(model_, subgraph_index, &subgraph),
               "LiteRtGetModelSubgraph") ||
        !Check(api_.GetNumSubgraphInputs(subgraph, &input_count),
               "LiteRtGetNumSubgraphInputs") ||
        !Check(api_.GetNumSubgraphOutputs(subgraph, &output_count),
               "LiteRtGetNumSubgraphOutputs")) {
      return false;
    }
    if (input_count != 1 || output_count != 1) {
      LOGE("Audio LiteRT model tensor count mismatch: expected 1 input/1 output, got %zu/%zu",
           input_count, output_count);
      return false;
    }
    LiteRtTensor input = nullptr;
    LiteRtTensor output = nullptr;
    if (!Check(api_.GetSubgraphInput(subgraph, 0, &input), "LiteRtGetSubgraphInput") ||
        !Check(api_.GetSubgraphOutput(subgraph, 0, &output), "LiteRtGetSubgraphOutput") ||
        !ReadTensorType(input, "input", &input_type_) ||
        !ReadTensorType(output, "output", &output_type_)) {
      return false;
    }
    input_element_count_ = ElementCount(input_type_);
    output_element_count_ = ElementCount(output_type_);
    if (!ShapeEquals(input_type_, expected_input_shape_)) {
      LOGE("Audio LiteRT input shape mismatch: expected %s got %s",
           DescribeShapeVector(expected_input_shape_).c_str(), DescribeShape(input_type_).c_str());
      return false;
    }
    if (!ShapeEquals(output_type_, expected_output_shape_)) {
      LOGE("Audio LiteRT output shape mismatch: expected %s got %s",
           DescribeShapeVector(expected_output_shape_).c_str(), DescribeShape(output_type_).c_str());
      return false;
    }
    LOGW("Audio LiteRT model shape input=%s output=%s accelerator=%s",
         DescribeShape(input_type_).c_str(), DescribeShape(output_type_).c_str(),
         use_npu_ ? "npu" : "cpu");
    return true;
  }

	  void DestroyBuffer(LiteRtTensorBuffer buffer) const {
	    if (buffer != nullptr && api_.DestroyTensorBuffer != nullptr) {
	      api_.DestroyTensorBuffer(buffer);
	    }
	  }

	  bool CreateTensorBuffers() {
	    LiteRtTensorBufferRequirements input_requirements = nullptr;
	    LiteRtTensorBufferRequirements output_requirements = nullptr;
	    if (!Check(api_.GetCompiledModelInputBufferRequirements(
	                   compiled_model_, 0, 0, &input_requirements),
	               "LiteRtGetCompiledModelInputBufferRequirements") ||
	        !Check(api_.GetCompiledModelOutputBufferRequirements(
	                   compiled_model_, 0, 0, &output_requirements),
	               "LiteRtGetCompiledModelOutputBufferRequirements") ||
	        !Check(api_.CreateManagedTensorBufferFromRequirements(
	                   environment_, &input_type_, input_requirements, &input_buffer_),
	               "LiteRtCreateManagedTensorBufferFromRequirements(input)") ||
	        !Check(api_.CreateManagedTensorBufferFromRequirements(
	                   environment_, &output_type_, output_requirements, &output_buffer_),
	               "LiteRtCreateManagedTensorBufferFromRequirements(output)")) {
	      DestroyBuffer(input_buffer_);
	      input_buffer_ = nullptr;
	      DestroyBuffer(output_buffer_);
	      output_buffer_ = nullptr;
	      return false;
	    }
	    return true;
	  }

	  std::vector<uint8_t> model_bytes_;
  bool use_npu_ = false;
  std::string dispatch_library_dir_;
  void* litert_lib_ = nullptr;
  LiteRtApi api_;
  LiteRtEnvironment environment_ = nullptr;
	  LiteRtModel model_ = nullptr;
	  LiteRtOptions options_ = nullptr;
	  LiteRtCompiledModel compiled_model_ = nullptr;
	  LiteRtTensorBuffer input_buffer_ = nullptr;
	  LiteRtTensorBuffer output_buffer_ = nullptr;
	  LiteRtRankedTensorType input_type_{};
  LiteRtRankedTensorType output_type_{};
  std::vector<int32_t> expected_input_shape_;
  std::vector<int32_t> expected_output_shape_;
  size_t input_element_count_ = 0;
  size_t output_element_count_ = 0;
  std::mutex mutex_;
};

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_google_research_air_cosmo_gatekeeping_models_AudioGatekeeperLiteRtImpl_nativeCreate(
    JNIEnv* env, jclass clazz, jbyteArray model_buffer, jboolean use_npu,
    jintArray expected_input_shape, jintArray expected_output_shape, jstring dispatch_library_dir) {
  (void)clazz;
  if (use_npu == JNI_TRUE && !CanInitializeGoogleTensorSouthBound()) {
    return 0;
  }
  auto model_bytes = CopyByteArray(env, model_buffer);
  if (!model_bytes.has_value()) {
    LOGE("nativeCreate received null or unreadable model buffer");
    return 0;
  }
  auto input_shape = CopyIntArray(env, expected_input_shape);
  auto output_shape = CopyIntArray(env, expected_output_shape);
  if (!input_shape.has_value() || !output_shape.has_value()) {
    LOGE("nativeCreate received unreadable expected tensor shape arrays");
    return 0;
  }
  auto model = std::make_unique<LiteRtAudioModel>();
  if (!model->Initialize(*model_bytes, use_npu == JNI_TRUE, std::move(*input_shape),
                         std::move(*output_shape), CopyString(env, dispatch_library_dir))) {
    return 0;
  }
  return reinterpret_cast<jlong>(model.release());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_google_research_air_cosmo_gatekeeping_models_AudioGatekeeperLiteRtImpl_nativeCanInitializeGoogleTensorNpu(
    JNIEnv* env, jclass clazz) {
  (void)env;
  (void)clazz;
  return CanInitializeGoogleTensorSouthBound() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_google_research_air_cosmo_gatekeeping_models_AudioGatekeeperLiteRtImpl_nativeRun(
    JNIEnv* env, jclass clazz, jlong handle, jobject input_buffer, jobject output_buffer) {
  (void)clazz;
  if (handle == 0 || input_buffer == nullptr || output_buffer == nullptr) {
    LOGE("nativeRun received null handle or buffers");
    return JNI_FALSE;
  }
  auto* model = reinterpret_cast<LiteRtAudioModel*>(handle);
  return model->Run(env, input_buffer, output_buffer) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_google_research_air_cosmo_gatekeeping_models_AudioGatekeeperLiteRtImpl_nativeDestroy(
    JNIEnv* env, jclass clazz, jlong handle) {
  (void)env;
  (void)clazz;
  if (handle == 0) {
    return;
  }
  auto* model = reinterpret_cast<LiteRtAudioModel*>(handle);
  model->Destroy();
  delete model;
}
