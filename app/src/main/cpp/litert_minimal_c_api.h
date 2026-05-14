// Minimal LiteRT C ABI declarations used by the BERT gatekeeper replacement.
//
// Pinned against google-ai-edge/LiteRT ref
// 47615eb6eaec25e8dfcd1aba922c560a57cba0a2, matching the local LiteRT-LM
// workspace. Keep this header intentionally small; add symbols only when the
// JNI implementation uses them.

#pragma once

#include <cstddef>
#include <cstdint>

enum LiteRtStatus {
  kLiteRtStatusOk = 0,
};

enum LiteRtHwAccelerators {
  kLiteRtHwAcceleratorCpu = 1 << 0,
  kLiteRtHwAcceleratorNpu = 1 << 2,
};

enum LiteRtElementType {
  kLiteRtElementTypeInt32 = 2,
  kLiteRtElementTypeFloat32 = 1,
};

enum LiteRtTensorTypeId {
  kLiteRtRankedTensorType = 0,
  kLiteRtUnrankedTensorType = 1,
};

using LiteRtHwAcceleratorSet = int;
using LiteRtParamIndex = size_t;
using LiteRtHostMemoryDeallocator = void (*)(void* addr);

class LiteRtEnvironmentT;
class LiteRtModelT;
class LiteRtOptionsT;
class LiteRtCompiledModelT;
class LiteRtTensorBufferT;
class LiteRtSubgraphT;
class LiteRtTensorT;

using LiteRtEnvironment = LiteRtEnvironmentT*;
using LiteRtModel = LiteRtModelT*;
using LiteRtOptions = LiteRtOptionsT*;
using LiteRtCompiledModel = LiteRtCompiledModelT*;
using LiteRtTensorBuffer = LiteRtTensorBufferT*;
using LiteRtSubgraph = LiteRtSubgraphT*;
using LiteRtTensor = LiteRtTensorT*;

struct LiteRtLayout {
  unsigned int rank : 7;
  bool has_strides : 1;
  int32_t dimensions[8];
  uint32_t strides[8];
};

struct LiteRtRankedTensorType {
  LiteRtElementType element_type;
  LiteRtLayout layout;
};

struct LiteRtApi {
  LiteRtStatus (*CreateEnvironment)(int, const void*, LiteRtEnvironment*) = nullptr;
  void (*DestroyEnvironment)(LiteRtEnvironment) = nullptr;
  LiteRtStatus (*CreateModelFromBuffer)(const void*, size_t, LiteRtModel*) = nullptr;
  void (*DestroyModel)(LiteRtModel) = nullptr;
  LiteRtStatus (*GetMainModelSubgraphIndex)(LiteRtModel, LiteRtParamIndex*) = nullptr;
  LiteRtStatus (*GetModelSubgraph)(LiteRtModel, LiteRtParamIndex, LiteRtSubgraph*) = nullptr;
  LiteRtStatus (*GetNumSubgraphInputs)(LiteRtSubgraph, LiteRtParamIndex*) = nullptr;
  LiteRtStatus (*GetSubgraphInput)(LiteRtSubgraph, LiteRtParamIndex, LiteRtTensor*) = nullptr;
  LiteRtStatus (*GetNumSubgraphOutputs)(LiteRtSubgraph, LiteRtParamIndex*) = nullptr;
  LiteRtStatus (*GetSubgraphOutput)(LiteRtSubgraph, LiteRtParamIndex, LiteRtTensor*) = nullptr;
  LiteRtStatus (*GetTensorName)(LiteRtTensor, const char**) = nullptr;
  LiteRtStatus (*GetTensorTypeId)(LiteRtTensor, LiteRtTensorTypeId*) = nullptr;
  LiteRtStatus (*GetRankedTensorType)(LiteRtTensor, LiteRtRankedTensorType*) = nullptr;
  LiteRtStatus (*CreateOptions)(LiteRtOptions*) = nullptr;
  void (*DestroyOptions)(LiteRtOptions) = nullptr;
  LiteRtStatus (*SetOptionsHardwareAccelerators)(LiteRtOptions, LiteRtHwAcceleratorSet) = nullptr;
  LiteRtStatus (*CreateCompiledModel)(LiteRtEnvironment, LiteRtModel, LiteRtOptions,
                                      LiteRtCompiledModel*) = nullptr;
  void (*DestroyCompiledModel)(LiteRtCompiledModel) = nullptr;
  LiteRtStatus (*CreateTensorBufferFromHostMemory)(const LiteRtRankedTensorType*, void*,
                                                   size_t, LiteRtHostMemoryDeallocator,
                                                   LiteRtTensorBuffer*) = nullptr;
  void (*DestroyTensorBuffer)(LiteRtTensorBuffer) = nullptr;
  LiteRtStatus (*RunCompiledModel)(LiteRtCompiledModel, LiteRtParamIndex, size_t,
                                   LiteRtTensorBuffer*, size_t, LiteRtTensorBuffer*) = nullptr;
  const char* (*GetStatusString)(LiteRtStatus) = nullptr;
};

inline LiteRtRankedTensorType MakeRankedTensorType(
    LiteRtElementType element_type, int32_t dim0, int32_t dim1) {
  LiteRtRankedTensorType tensor_type{};
  tensor_type.element_type = element_type;
  tensor_type.layout.rank = 2;
  tensor_type.layout.has_strides = false;
  tensor_type.layout.dimensions[0] = dim0;
  tensor_type.layout.dimensions[1] = dim1;
  return tensor_type;
}

inline LiteRtRankedTensorType MakeRankedTensorType(
    LiteRtElementType element_type, const int32_t* dimensions, unsigned int rank) {
  LiteRtRankedTensorType tensor_type{};
  tensor_type.element_type = element_type;
  tensor_type.layout.rank = rank;
  tensor_type.layout.has_strides = false;
  for (unsigned int i = 0; i < rank && i < 8; ++i) {
    tensor_type.layout.dimensions[i] = dimensions[i];
  }
  return tensor_type;
}
