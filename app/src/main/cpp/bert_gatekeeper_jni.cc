#include <jni.h>

#include <android/log.h>
#include <dlfcn.h>

#include "litert_minimal_c_api.h"

#include <algorithm>
#include <array>
#include <cstdint>
#include <cstring>
#include <cwctype>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

namespace {

constexpr char kLogTag[] = "BertGatekeeperJni";
constexpr int kSequenceLength = 512;
constexpr int kOutputLength = 19;
constexpr int kMaxInputCharsPerWord = 100;

constexpr int kPadId = 0;
constexpr int kUnkId = 100;
constexpr int kClsId = 101;
constexpr int kSepId = 102;

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, kLogTag, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, kLogTag, __VA_ARGS__)

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

bool LogAndClearException(JNIEnv* env, const char* context) {
  if (!env->ExceptionCheck()) {
    return false;
  }
  LOGE("%s threw a Java exception", context);
  env->ExceptionDescribe();
  env->ExceptionClear();
  return true;
}

std::string EncodeUtf8(uint32_t codepoint) {
  std::string out;
  if (codepoint <= 0x7F) {
    out.push_back(static_cast<char>(codepoint));
  } else if (codepoint <= 0x7FF) {
    out.push_back(static_cast<char>(0xC0 | (codepoint >> 6)));
    out.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
  } else if (codepoint <= 0xFFFF) {
    out.push_back(static_cast<char>(0xE0 | (codepoint >> 12)));
    out.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
    out.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
  } else {
    out.push_back(static_cast<char>(0xF0 | (codepoint >> 18)));
    out.push_back(static_cast<char>(0x80 | ((codepoint >> 12) & 0x3F)));
    out.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
    out.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
  }
  return out;
}

std::vector<uint32_t> DecodeUtf8(const std::string& input) {
  std::vector<uint32_t> out;
  size_t i = 0;
  while (i < input.size()) {
    const uint8_t c = static_cast<uint8_t>(input[i]);
    if (c < 0x80) {
      out.push_back(c);
      ++i;
    } else if ((c >> 5) == 0x6 && i + 1 < input.size()) {
      out.push_back(((c & 0x1F) << 6) | (static_cast<uint8_t>(input[i + 1]) & 0x3F));
      i += 2;
    } else if ((c >> 4) == 0xE && i + 2 < input.size()) {
      out.push_back(((c & 0x0F) << 12) |
                    ((static_cast<uint8_t>(input[i + 1]) & 0x3F) << 6) |
                    (static_cast<uint8_t>(input[i + 2]) & 0x3F));
      i += 3;
    } else if ((c >> 3) == 0x1E && i + 3 < input.size()) {
      out.push_back(((c & 0x07) << 18) |
                    ((static_cast<uint8_t>(input[i + 1]) & 0x3F) << 12) |
                    ((static_cast<uint8_t>(input[i + 2]) & 0x3F) << 6) |
                    (static_cast<uint8_t>(input[i + 3]) & 0x3F));
      i += 4;
    } else {
      out.push_back(0xFFFD);
      ++i;
    }
  }
  return out;
}

bool IsWhitespace(uint32_t codepoint) {
  return codepoint == 0x20 || codepoint == 0x09 || codepoint == 0x0A || codepoint == 0x0D;
}

bool IsControl(uint32_t codepoint) {
  if (codepoint == 0x09 || codepoint == 0x0A || codepoint == 0x0D) {
    return false;
  }
  return codepoint < 0x20 || (codepoint >= 0x7F && codepoint <= 0x9F);
}

bool IsPunctuation(uint32_t codepoint) {
  if ((codepoint >= 33 && codepoint <= 47) ||
      (codepoint >= 58 && codepoint <= 64) ||
      (codepoint >= 91 && codepoint <= 96) ||
      (codepoint >= 123 && codepoint <= 126)) {
    return true;
  }
  return (codepoint >= 0x2000 && codepoint <= 0x206F) ||
         (codepoint >= 0x2E00 && codepoint <= 0x2E7F);
}

bool IsCjk(uint32_t codepoint) {
  return (codepoint >= 0x4E00 && codepoint <= 0x9FFF) ||
         (codepoint >= 0x3400 && codepoint <= 0x4DBF) ||
         (codepoint >= 0x20000 && codepoint <= 0x2A6DF) ||
         (codepoint >= 0x2A700 && codepoint <= 0x2B73F) ||
         (codepoint >= 0x2B740 && codepoint <= 0x2B81F) ||
         (codepoint >= 0x2B820 && codepoint <= 0x2CEAF) ||
         (codepoint >= 0xF900 && codepoint <= 0xFAFF) ||
         (codepoint >= 0x2F800 && codepoint <= 0x2FA1F);
}

bool IsCombiningMark(uint32_t codepoint) {
  return (codepoint >= 0x0300 && codepoint <= 0x036F) ||
         (codepoint >= 0x1AB0 && codepoint <= 0x1AFF) ||
         (codepoint >= 0x1DC0 && codepoint <= 0x1DFF) ||
         (codepoint >= 0x20D0 && codepoint <= 0x20FF) ||
         (codepoint >= 0xFE20 && codepoint <= 0xFE2F);
}

uint32_t StripLatinAccent(uint32_t codepoint) {
  switch (codepoint) {
    case 0x00C0: case 0x00C1: case 0x00C2: case 0x00C3: case 0x00C4:
    case 0x00C5: case 0x0100: case 0x0102: case 0x0104:
    case 0x00E0: case 0x00E1: case 0x00E2: case 0x00E3: case 0x00E4:
    case 0x00E5: case 0x0101: case 0x0103: case 0x0105:
      return 'a';
    case 0x00C7: case 0x0106: case 0x0108: case 0x010A: case 0x010C:
    case 0x00E7: case 0x0107: case 0x0109: case 0x010B: case 0x010D:
      return 'c';
    case 0x00D0: case 0x010E: case 0x0110:
    case 0x00F0: case 0x010F: case 0x0111:
      return 'd';
    case 0x00C8: case 0x00C9: case 0x00CA: case 0x00CB: case 0x0112:
    case 0x0114: case 0x0116: case 0x0118: case 0x011A:
    case 0x00E8: case 0x00E9: case 0x00EA: case 0x00EB: case 0x0113:
    case 0x0115: case 0x0117: case 0x0119: case 0x011B:
      return 'e';
    case 0x011C: case 0x011E: case 0x0120: case 0x0122:
    case 0x011D: case 0x011F: case 0x0121: case 0x0123:
      return 'g';
    case 0x0124: case 0x0126: case 0x0125: case 0x0127:
      return 'h';
    case 0x00CC: case 0x00CD: case 0x00CE: case 0x00CF: case 0x0128:
    case 0x012A: case 0x012C: case 0x012E: case 0x0130:
    case 0x00EC: case 0x00ED: case 0x00EE: case 0x00EF: case 0x0129:
    case 0x012B: case 0x012D: case 0x012F:
      return 'i';
    case 0x0134: case 0x0135:
      return 'j';
    case 0x0136: case 0x0137: case 0x0138:
      return 'k';
    case 0x0139: case 0x013B: case 0x013D: case 0x013F: case 0x0141:
    case 0x013A: case 0x013C: case 0x013E: case 0x0140: case 0x0142:
      return 'l';
    case 0x00D1: case 0x0143: case 0x0145: case 0x0147:
    case 0x00F1: case 0x0144: case 0x0146: case 0x0148: case 0x0149:
      return 'n';
    case 0x00D2: case 0x00D3: case 0x00D4: case 0x00D5: case 0x00D6:
    case 0x00D8: case 0x014C: case 0x014E: case 0x0150:
    case 0x00F2: case 0x00F3: case 0x00F4: case 0x00F5: case 0x00F6:
    case 0x00F8: case 0x014D: case 0x014F: case 0x0151:
      return 'o';
    case 0x0154: case 0x0156: case 0x0158:
    case 0x0155: case 0x0157: case 0x0159:
      return 'r';
    case 0x015A: case 0x015C: case 0x015E: case 0x0160:
    case 0x015B: case 0x015D: case 0x015F: case 0x0161:
      return 's';
    case 0x0162: case 0x0164: case 0x0166:
    case 0x0163: case 0x0165: case 0x0167:
      return 't';
    case 0x00D9: case 0x00DA: case 0x00DB: case 0x00DC: case 0x0168:
    case 0x016A: case 0x016C: case 0x016E: case 0x0170: case 0x0172:
    case 0x00F9: case 0x00FA: case 0x00FB: case 0x00FC: case 0x0169:
    case 0x016B: case 0x016D: case 0x016F: case 0x0171: case 0x0173:
      return 'u';
    case 0x0174: case 0x0175:
      return 'w';
    case 0x00DD: case 0x0176: case 0x0178:
    case 0x00FD: case 0x00FF: case 0x0177:
      return 'y';
    case 0x0179: case 0x017B: case 0x017D:
    case 0x017A: case 0x017C: case 0x017E:
      return 'z';
    default:
      return codepoint;
  }
}

uint32_t LowercaseUnicode(uint32_t codepoint) {
  codepoint = StripLatinAccent(codepoint);
  if (codepoint >= 'A' && codepoint <= 'Z') {
    return codepoint + ('a' - 'A');
  }
  if (codepoint <= 0x10FFFF) {
    return static_cast<uint32_t>(std::towlower(static_cast<wint_t>(codepoint)));
  }
  return codepoint;
}

class BertTokenizer {
 public:
  bool Load(const std::vector<uint8_t>& vocab_bytes) {
    token_to_id_.clear();

    size_t line_start = 0;
    int token_id = 0;
    for (size_t i = 0; i <= vocab_bytes.size(); ++i) {
      if (i != vocab_bytes.size() && vocab_bytes[i] != '\n') {
        continue;
      }

      size_t line_end = i;
      if (line_end > line_start && vocab_bytes[line_end - 1] == '\r') {
        --line_end;
      }

      std::string token(reinterpret_cast<const char*>(vocab_bytes.data() + line_start),
                        line_end - line_start);
      token_to_id_.emplace(std::move(token), token_id++);
      line_start = i + 1;
    }

    return IdForToken("[PAD]") == kPadId &&
           IdForToken("[UNK]") == kUnkId &&
           IdForToken("[CLS]") == kClsId &&
           IdForToken("[SEP]") == kSepId;
  }

  std::array<int32_t, kSequenceLength> EncodeWordIds(const std::string& text) const {
    std::array<int32_t, kSequenceLength> ids{};
    ids.fill(kPadId);

    std::vector<int32_t> pieces;
    pieces.reserve(kSequenceLength);
    pieces.push_back(kClsId);

    for (const std::string& token : BasicTokenize(text)) {
      for (const int32_t id : WordPieceTokenize(token)) {
        if (pieces.size() >= kSequenceLength - 1) {
          break;
        }
        pieces.push_back(id);
      }
      if (pieces.size() >= kSequenceLength - 1) {
        break;
      }
    }

    pieces.push_back(kSepId);
    std::copy(pieces.begin(), pieces.end(), ids.begin());
    return ids;
  }

 private:
  int32_t IdForToken(const std::string& token) const {
    const auto it = token_to_id_.find(token);
    if (it == token_to_id_.end()) {
      return -1;
    }
    return it->second;
  }

  std::vector<std::string> BasicTokenize(const std::string& text) const {
    std::vector<std::string> tokens;
    std::string current;

    const auto flush = [&tokens, &current]() {
      if (!current.empty()) {
        tokens.push_back(current);
        current.clear();
      }
    };

    for (uint32_t codepoint : DecodeUtf8(text)) {
      if (IsControl(codepoint)) {
        continue;
      }
      if (IsCombiningMark(codepoint)) {
        continue;
      }
      if (IsWhitespace(codepoint)) {
        flush();
        continue;
      }

      codepoint = LowercaseUnicode(codepoint);
      if (IsCjk(codepoint) || IsPunctuation(codepoint)) {
        flush();
        tokens.push_back(EncodeUtf8(codepoint));
      } else {
        current += EncodeUtf8(codepoint);
      }
    }
    flush();
    return tokens;
  }

  std::vector<int32_t> WordPieceTokenize(const std::string& token) const {
    const std::vector<uint32_t> codepoints = DecodeUtf8(token);
    if (codepoints.size() > kMaxInputCharsPerWord) {
      return {kUnkId};
    }

    std::vector<std::string> chars;
    chars.reserve(codepoints.size());
    for (uint32_t codepoint : codepoints) {
      chars.push_back(EncodeUtf8(codepoint));
    }

    std::vector<int32_t> output;
    size_t start = 0;
    while (start < chars.size()) {
      size_t end = chars.size();
      int32_t current_id = -1;
      size_t matched_end = start;

      while (start < end) {
        std::string candidate;
        if (start > 0) {
          candidate = "##";
        }
        for (size_t i = start; i < end; ++i) {
          candidate += chars[i];
        }

        current_id = IdForToken(candidate);
        if (current_id >= 0) {
          matched_end = end;
          break;
        }
        --end;
      }

      if (current_id < 0) {
        return {kUnkId};
      }
      output.push_back(current_id);
      start = matched_end;
    }
    return output;
  }

  std::unordered_map<std::string, int32_t> token_to_id_;
};

struct BertInputs {
  std::array<int32_t, kSequenceLength> word_ids{};
  std::array<int32_t, kSequenceLength> type_ids{};
  std::array<int32_t, kSequenceLength> mask{};
};

class BertModelBackend {
 public:
  bool Initialize(JNIEnv* env, const std::vector<uint8_t>& model_bytes, bool accelerator_requested) {
    (void)env;
    model_size_ = model_bytes.size();
    accelerator_requested_ = accelerator_requested;

    if (!LoadLiteRtSymbols()) {
      return false;
    }

    if (!Check(api_.CreateEnvironment(0, nullptr, &environment_), "LiteRtCreateEnvironment") ||
        !Check(api_.CreateModelFromBuffer(model_bytes.data(), model_bytes.size(), &model_),
               "LiteRtCreateModelFromBuffer") ||
        !ValidateModelContract() ||
        !Check(api_.CreateOptions(&options_), "LiteRtCreateOptions")) {
      Destroy(env);
      return false;
    }

    const LiteRtHwAcceleratorSet accelerators =
        accelerator_requested ? kLiteRtHwAcceleratorNpu : kLiteRtHwAcceleratorCpu;
    if (!Check(api_.SetOptionsHardwareAccelerators(options_, accelerators),
               "LiteRtSetOptionsHardwareAccelerators") ||
        !Check(api_.CreateCompiledModel(environment_, model_, options_, &compiled_model_),
               "LiteRtCreateCompiledModel") ||
        !CreateTensorBuffers()) {
      Destroy(env);
      return false;
    }

    return true;
  }

  jfloatArray Run(JNIEnv* env, const BertInputs& inputs) {
    if (compiled_model_ == nullptr) {
      LOGE("LiteRT backend is not initialized");
      return nullptr;
    }

    word_ids_ = inputs.word_ids;
    type_ids_ = inputs.type_ids;
    mask_ = inputs.mask;
    output_.fill(0.0f);

    if (!Check(api_.RunCompiledModel(compiled_model_, 0, input_buffers_.size(),
                                     input_buffers_.data(), output_buffers_.size(),
                                     output_buffers_.data()),
               "LiteRtRunCompiledModel")) {
      return nullptr;
    }

    jfloatArray result = env->NewFloatArray(kOutputLength);
    if (result == nullptr) {
      return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, kOutputLength, output_.data());
    return result;
  }

  void Destroy(JNIEnv* env) {
    (void)env;
    for (LiteRtTensorBuffer& buffer : input_buffers_) {
      if (buffer != nullptr && api_.DestroyTensorBuffer != nullptr) {
        api_.DestroyTensorBuffer(buffer);
        buffer = nullptr;
      }
    }
    for (LiteRtTensorBuffer& buffer : output_buffers_) {
      if (buffer != nullptr && api_.DestroyTensorBuffer != nullptr) {
        api_.DestroyTensorBuffer(buffer);
        buffer = nullptr;
      }
    }
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

  size_t model_size() const { return model_size_; }
  bool accelerator_requested() const { return accelerator_requested_; }

 private:
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
           Resolve("LiteRtCreateTensorBufferFromHostMemory",
                   &api_.CreateTensorBufferFromHostMemory) &&
           Resolve("LiteRtDestroyTensorBuffer", &api_.DestroyTensorBuffer) &&
           Resolve("LiteRtRunCompiledModel", &api_.RunCompiledModel) &&
           ResolveOptional("LiteRtGetStatusString", &api_.GetStatusString);
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

  template <typename Fn>
  bool ResolveOptional(const char* symbol, Fn* out) {
    dlerror();
    *out = reinterpret_cast<Fn>(dlsym(litert_lib_, symbol));
    if (*out == nullptr) {
      LOGW("Optional symbol %s missing from libLiteRt.so; status messages will be unavailable",
           symbol);
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

  bool ValidateTensor(LiteRtTensor tensor, const char* expected_name,
                      LiteRtElementType expected_element_type, int32_t expected_dim0,
                      int32_t expected_dim1,
                      const char* role, LiteRtParamIndex index) const {
    LiteRtTensorTypeId type_id = kLiteRtUnrankedTensorType;
    if (!Check(api_.GetTensorTypeId(tensor, &type_id), "LiteRtGetTensorTypeId")) {
      return false;
    }
    if (type_id != kLiteRtRankedTensorType) {
      LOGE("%s tensor %zu is not ranked", role, index);
      return false;
    }

    LiteRtRankedTensorType tensor_type{};
    if (!Check(api_.GetRankedTensorType(tensor, &tensor_type), "LiteRtGetRankedTensorType")) {
      return false;
    }

    const char* tensor_name = "";
    if (!Check(api_.GetTensorName(tensor, &tensor_name), "LiteRtGetTensorName")) {
      return false;
    }

    if (tensor_name == nullptr || std::strcmp(tensor_name, expected_name) != 0 ||
        tensor_type.element_type != expected_element_type ||
        tensor_type.layout.rank != 2 ||
        tensor_type.layout.dimensions[0] != expected_dim0 ||
        tensor_type.layout.dimensions[1] != expected_dim1) {
      LOGE("%s tensor %zu contract mismatch expected_name=%s name=%s type=%d rank=%u dims=[%d,%d]",
           role, index, expected_name, tensor_name == nullptr ? "" : tensor_name,
           static_cast<int>(tensor_type.element_type), tensor_type.layout.rank,
           tensor_type.layout.dimensions[0], tensor_type.layout.dimensions[1]);
      return false;
    }
    return true;
  }

  bool ValidateModelContract() const {
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

    if (input_count != 3 || output_count != 1) {
      LOGE("BERT model tensor count mismatch: expected 3 inputs/1 output, got %zu/%zu",
           input_count, output_count);
      return false;
    }

    constexpr const char* kInputNames[] = {
        "serving_default_input_word_ids:0",
        "serving_default_input_type_ids:0",
        "serving_default_input_mask:0",
    };
    for (LiteRtParamIndex i = 0; i < input_count; ++i) {
      LiteRtTensor tensor = nullptr;
      if (!Check(api_.GetSubgraphInput(subgraph, i, &tensor), "LiteRtGetSubgraphInput") ||
          !ValidateTensor(tensor, kInputNames[i], kLiteRtElementTypeInt32, 1, kSequenceLength,
                          "input", i)) {
        return false;
      }
    }

    LiteRtTensor output = nullptr;
    return Check(api_.GetSubgraphOutput(subgraph, 0, &output), "LiteRtGetSubgraphOutput") &&
           ValidateTensor(output, "StatefulPartitionedCall:0", kLiteRtElementTypeFloat32, 1,
                          kOutputLength, "output", 0);
  }

  bool CreateTensorBuffers() {
    const LiteRtRankedTensorType input_type =
        MakeRankedTensorType(kLiteRtElementTypeInt32, 1, kSequenceLength);
    const LiteRtRankedTensorType output_type =
        MakeRankedTensorType(kLiteRtElementTypeFloat32, 1, kOutputLength);

    input_buffers_[0] = CreateHostBuffer(input_type, word_ids_.data(), sizeof(int32_t) * word_ids_.size());
    input_buffers_[1] = CreateHostBuffer(input_type, type_ids_.data(), sizeof(int32_t) * type_ids_.size());
    input_buffers_[2] = CreateHostBuffer(input_type, mask_.data(), sizeof(int32_t) * mask_.size());
    output_buffers_[0] = CreateHostBuffer(output_type, output_.data(), sizeof(float) * output_.size());

    return input_buffers_[0] != nullptr && input_buffers_[1] != nullptr &&
           input_buffers_[2] != nullptr && output_buffers_[0] != nullptr;
  }

  LiteRtTensorBuffer CreateHostBuffer(const LiteRtRankedTensorType& tensor_type, void* data,
                                      size_t size) {
    LiteRtTensorBuffer buffer = nullptr;
    if (!Check(api_.CreateTensorBufferFromHostMemory(&tensor_type, data, size, nullptr, &buffer),
               "LiteRtCreateTensorBufferFromHostMemory")) {
      return nullptr;
    }
    return buffer;
  }

  size_t model_size_ = 0;
  bool accelerator_requested_ = false;
  void* litert_lib_ = nullptr;
  LiteRtApi api_;
  LiteRtEnvironment environment_ = nullptr;
  LiteRtModel model_ = nullptr;
  LiteRtOptions options_ = nullptr;
  LiteRtCompiledModel compiled_model_ = nullptr;
  std::array<LiteRtTensorBuffer, 3> input_buffers_{};
  std::array<LiteRtTensorBuffer, 1> output_buffers_{};
  alignas(64) std::array<int32_t, kSequenceLength> word_ids_{};
  alignas(64) std::array<int32_t, kSequenceLength> type_ids_{};
  alignas(64) std::array<int32_t, kSequenceLength> mask_{};
  alignas(64) std::array<float, kOutputLength> output_{};
};

struct BertGatekeeperHandle {
  std::vector<uint8_t> vocab_bytes;
  std::vector<uint8_t> model_bytes;
  BertTokenizer tokenizer;
  BertModelBackend backend;
  std::mutex mutex;
};

BertInputs BuildInputs(const BertTokenizer& tokenizer, const std::string& query, jint segment_id_override) {
  BertInputs inputs;
  inputs.word_ids = tokenizer.EncodeWordIds(query);

  const int32_t segment_id = segment_id_override >= 0 ? segment_id_override : 0;
  for (size_t i = 0; i < inputs.word_ids.size(); ++i) {
    if (inputs.word_ids[i] == kPadId) {
      inputs.type_ids[i] = 0;
      inputs.mask[i] = 0;
    } else {
      inputs.type_ids[i] = segment_id;
      inputs.mask[i] = 1;
    }
  }
  return inputs;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_google_research_air_cosmo_gatekeeping_models_BertEncoderImpl_nativeCreate(
    JNIEnv* env, jclass clazz, jbyteArray vocab_buffer, jbyteArray model_buffer,
    jboolean is_tpu_enabled) {
  (void)clazz;

  auto vocab_bytes = CopyByteArray(env, vocab_buffer);
  auto model_bytes = CopyByteArray(env, model_buffer);
  if (!vocab_bytes.has_value() || !model_bytes.has_value()) {
    LOGE("nativeCreate received null or unreadable buffers");
    return 0;
  }

  auto handle = std::make_unique<BertGatekeeperHandle>();
  handle->vocab_bytes = std::move(*vocab_bytes);
  handle->model_bytes = std::move(*model_bytes);

  if (!handle->tokenizer.Load(handle->vocab_bytes)) {
    LOGE("nativeCreate failed to load BERT vocab or special token IDs");
    return 0;
  }

  if (!handle->backend.Initialize(env, handle->model_bytes, is_tpu_enabled == JNI_TRUE)) {
    LOGE("nativeCreate failed to compile LiteRT model; accelerator_requested=%s model_bytes=%zu",
         is_tpu_enabled == JNI_TRUE ? "true" : "false", handle->model_bytes.size());
    return 0;
  }

  LOGW("nativeCreate compiled LiteRT model; accelerator_requested=%s model_bytes=%zu",
       is_tpu_enabled == JNI_TRUE ? "true" : "false", handle->backend.model_size());

  return reinterpret_cast<jlong>(handle.release());
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_google_research_air_cosmo_gatekeeping_models_BertEncoderImpl_nativeRunStandardEncoder(
    JNIEnv* env, jclass clazz, jlong handle_value, jstring query, jint segment_id_override) {
  (void)clazz;

  auto* handle = reinterpret_cast<BertGatekeeperHandle*>(handle_value);
  if (handle == nullptr) {
    LOGE("nativeRunStandardEncoder received null handle");
    return nullptr;
  }

  std::lock_guard<std::mutex> lock(handle->mutex);
  const std::string query_text = CopyString(env, query);
  const BertInputs inputs = BuildInputs(handle->tokenizer, query_text, segment_id_override);

  return handle->backend.Run(env, inputs);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_google_research_air_cosmo_gatekeeping_models_BertEncoderImpl_nativeRunMultiEncoder(
    JNIEnv* env, jclass clazz, jlong handle, jstring query, jint segment_id_override) {
  (void)env;
  (void)clazz;
  (void)handle;
  (void)query;
  (void)segment_id_override;
  LOGW("nativeRunMultiEncoder is not implemented");
  return nullptr;
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_google_research_air_cosmo_gatekeeping_models_BertEncoderImpl_nativeDebugBuildInputs(
    JNIEnv* env, jclass clazz, jlong handle_value, jstring query, jint segment_id_override) {
  (void)clazz;

  auto* handle = reinterpret_cast<BertGatekeeperHandle*>(handle_value);
  if (handle == nullptr) {
    LOGE("nativeDebugBuildInputs received null handle");
    return nullptr;
  }

  std::lock_guard<std::mutex> lock(handle->mutex);
  const std::string query_text = CopyString(env, query);
  const BertInputs inputs = BuildInputs(handle->tokenizer, query_text, segment_id_override);

  constexpr int kDebugInputLength = kSequenceLength * 3;
  std::array<jint, kDebugInputLength> packed{};
  std::copy(inputs.word_ids.begin(), inputs.word_ids.end(), packed.begin());
  std::copy(inputs.type_ids.begin(), inputs.type_ids.end(), packed.begin() + kSequenceLength);
  std::copy(inputs.mask.begin(), inputs.mask.end(), packed.begin() + (kSequenceLength * 2));

  jintArray result = env->NewIntArray(kDebugInputLength);
  if (result == nullptr) {
    return nullptr;
  }
  env->SetIntArrayRegion(result, 0, kDebugInputLength, packed.data());
  return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_google_research_air_cosmo_gatekeeping_models_BertEncoderImpl_nativeDestroy(
    JNIEnv* env, jclass clazz, jlong handle_value) {
  (void)clazz;
  auto* handle = reinterpret_cast<BertGatekeeperHandle*>(handle_value);
  if (handle != nullptr) {
    handle->backend.Destroy(env);
  }
  delete handle;
}
