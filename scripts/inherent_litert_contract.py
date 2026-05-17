"""Pure-Python contract checks for the Android inherent LiteRT artifact."""

from __future__ import annotations

import json
import struct
from pathlib import Path
from typing import Any


EXPECTED_INPUT_SHAPE = [1, 3000, 128]
EXPECTED_OUTPUT_SHAPE = [1, 13]
EXPECTED_HEAD_ORDER = [
    "isInteresting",
    "hasAddToListIntent",
    "hasTermSearchQuery",
    "hasPhotoQuery",
    "hasCalendarEvent",
    "hasCreateDocIntent",
    "hasPersonContext",
    "hasEventContext",
    "hasDeepResearchIntent",
    "hasInsightIntent",
    "hasBrowsingAgentIntent",
    "hasCallingAgentIntent",
    "hasStartTimerIntent",
]
EXPECTED_THRESHOLD_KEYS = [
    "is_interesting",
    "has_add_to_list_intent",
    "has_term_search_query",
    "has_photo_query",
    "has_calendar_event",
    "has_create_doc_intent",
    "has_person_context",
    "has_event_context",
    "has_deep_research_intent",
    "has_insight_intent",
    "has_browsing_agent_intent",
    "has_calling_agent_intent",
    "has_start_timer_intent",
]

TENSOR_TYPE_FLOAT32 = 0


class ContractError(ValueError):
    """Raised when the inherent artifact no longer matches Android's contract."""


class _FlatBuffer:
    def __init__(self, data: bytes) -> None:
        self.data = data

    def u8(self, offset: int) -> int:
        return self.data[offset]

    def u16(self, offset: int) -> int:
        return struct.unpack_from("<H", self.data, offset)[0]

    def i32(self, offset: int) -> int:
        return struct.unpack_from("<i", self.data, offset)[0]

    def u32(self, offset: int) -> int:
        return struct.unpack_from("<I", self.data, offset)[0]

    def root_table(self) -> int:
        if len(self.data) < 8:
            raise ContractError("TFLite file is too small to be a FlatBuffer")
        if self.data[4:8] != b"TFL3":
            raise ContractError("TFLite FlatBuffer identifier must be TFL3")
        return self.u32(0)

    def table_field(self, table: int, field: int) -> int | None:
        vtable = table - self.i32(table)
        vtable_size = self.u16(vtable)
        slot = 4 + field * 2
        if slot + 2 > vtable_size:
            return None
        offset = self.u16(vtable + slot)
        return table + offset if offset != 0 else None

    def table_ref(self, table: int, field: int) -> int | None:
        slot = self.table_field(table, field)
        return None if slot is None else slot + self.u32(slot)

    def vector_ref(self, table: int, field: int) -> int | None:
        return self.table_ref(table, field)

    def vector_length(self, vector: int) -> int:
        return self.u32(vector)

    def vector_ints(self, vector: int) -> list[int]:
        length = self.vector_length(vector)
        start = vector + 4
        return [self.i32(start + index * 4) for index in range(length)]

    def vector_table(self, vector: int, index: int) -> int:
        if index >= self.vector_length(vector):
            raise ContractError(f"FlatBuffer vector index out of range: {index}")
        slot = vector + 4 + index * 4
        return slot + self.u32(slot)

    def string_ref(self, table: int, field: int) -> str | None:
        ref = self.table_ref(table, field)
        if ref is None:
            return None
        length = self.u32(ref)
        start = ref + 4
        return self.data[start : start + length].decode("utf-8", errors="replace")


def _first_subgraph(buffer: _FlatBuffer) -> int:
    model = buffer.root_table()
    subgraphs = buffer.vector_ref(model, 2)
    if subgraphs is None or buffer.vector_length(subgraphs) < 1:
        raise ContractError("TFLite model must contain at least one subgraph")
    return buffer.vector_table(subgraphs, 0)


def _tensor(buffer: _FlatBuffer, subgraph: int, tensor_index: int) -> int:
    tensors = buffer.vector_ref(subgraph, 0)
    if tensors is None:
        raise ContractError("TFLite subgraph is missing tensor table")
    return buffer.vector_table(tensors, tensor_index)


def _single_tensor_index(buffer: _FlatBuffer, subgraph: int, field: int, label: str) -> int:
    values = buffer.vector_ref(subgraph, field)
    if values is None:
        raise ContractError(f"TFLite subgraph is missing {label} vector")
    indices = buffer.vector_ints(values)
    if len(indices) != 1:
        raise ContractError(f"TFLite model must have exactly one {label}, got {len(indices)}")
    return indices[0]


def _tensor_shape(buffer: _FlatBuffer, tensor: int, label: str) -> list[int]:
    shape = buffer.vector_ref(tensor, 0)
    if shape is None:
        raise ContractError(f"TFLite {label} tensor is missing shape")
    return buffer.vector_ints(shape)


def _tensor_type(buffer: _FlatBuffer, tensor: int) -> int:
    slot = buffer.table_field(tensor, 1)
    return TENSOR_TYPE_FLOAT32 if slot is None else buffer.u8(slot)


def inspect_tflite_contract(model_path: Path) -> dict[str, Any]:
    buffer = _FlatBuffer(model_path.read_bytes())
    subgraph = _first_subgraph(buffer)
    input_tensor = _tensor(buffer, subgraph, _single_tensor_index(buffer, subgraph, 1, "input"))
    output_tensor = _tensor(buffer, subgraph, _single_tensor_index(buffer, subgraph, 2, "output"))
    return {
        "input_shape": _tensor_shape(buffer, input_tensor, "input"),
        "input_type": _tensor_type(buffer, input_tensor),
        "input_name": buffer.string_ref(input_tensor, 3),
        "output_shape": _tensor_shape(buffer, output_tensor, "output"),
        "output_type": _tensor_type(buffer, output_tensor),
        "output_name": buffer.string_ref(output_tensor, 3),
    }


def validate_metadata(metadata: dict[str, Any]) -> None:
    if metadata.get("input_tensor") != "mel_spectrogram":
        raise ContractError("inherent metadata input_tensor must be mel_spectrogram")
    if metadata.get("output_tensor") != "intent_output":
        raise ContractError("inherent metadata output_tensor must be intent_output")
    if metadata.get("head_order") != EXPECTED_HEAD_ORDER:
        raise ContractError("inherent metadata head_order does not match Android's fixed head order")
    if metadata.get("threshold_keys_in_order") != EXPECTED_THRESHOLD_KEYS:
        raise ContractError("inherent metadata threshold_keys_in_order does not match Android's fixed key order")
    default_thresholds = metadata.get("default_thresholds")
    if not isinstance(default_thresholds, dict):
        raise ContractError("inherent metadata default_thresholds must be an object")
    missing = [key for key in EXPECTED_THRESHOLD_KEYS if key not in default_thresholds]
    if missing:
        raise ContractError(f"inherent metadata missing thresholds for: {', '.join(missing)}")

    config_model = metadata.get("config", {}).get("model", {})
    if config_model:
        if config_model.get("max_frames") != EXPECTED_INPUT_SHAPE[1]:
            raise ContractError("inherent metadata config.model.max_frames must be 3000")
        if config_model.get("mel_bins") != EXPECTED_INPUT_SHAPE[2]:
            raise ContractError("inherent metadata config.model.mel_bins must be 128")
        if config_model.get("num_heads") != EXPECTED_OUTPUT_SHAPE[1]:
            raise ContractError("inherent metadata config.model.num_heads must be 13")


def validate_tflite(model_path: Path) -> dict[str, Any]:
    contract = inspect_tflite_contract(model_path)
    if contract["input_shape"] != EXPECTED_INPUT_SHAPE:
        raise ContractError(
            f"inherent.tflite input shape must be {EXPECTED_INPUT_SHAPE}, got {contract['input_shape']}"
        )
    if contract["input_type"] != TENSOR_TYPE_FLOAT32:
        raise ContractError(f"inherent.tflite input dtype must be FLOAT32, got {contract['input_type']}")
    if contract["output_shape"] != EXPECTED_OUTPUT_SHAPE:
        raise ContractError(
            f"inherent.tflite output shape must be {EXPECTED_OUTPUT_SHAPE}, got {contract['output_shape']}"
        )
    if contract["output_type"] != TENSOR_TYPE_FLOAT32:
        raise ContractError(f"inherent.tflite output dtype must be FLOAT32, got {contract['output_type']}")
    return contract


def validate_artifact(model_path: Path, metadata_path: Path) -> dict[str, Any]:
    with metadata_path.open("r", encoding="utf-8") as handle:
        metadata = json.load(handle)
    validate_metadata(metadata)
    return validate_tflite(model_path)
