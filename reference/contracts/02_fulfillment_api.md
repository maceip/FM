# Contract 02 — Fulfillment API

**Producer:** WS3
**Consumers:** WS1 (Ask input → answer rendering), WS4 (skill execution dispatches through this)
**Status:** must land Week 1. Stable through M2; Hybrid additions in M3 are additive only.

## Purpose

The single entry point for "turn a request into an answer." Hides whether the answer came from cloud, on-device LLM, or a hybrid path. Streams tokens. Reports the path it took so callers can render latency, cost hints, or source labels.

## Request

```kotlin
data class FulfillmentRequest(
    val id: String,                       // ULID, set by caller
    val prompt: String,                   // user-facing text, post-ASR if voice
    val mode: FulfillmentMode,            // current Settings value at request time
    val contextEvents: List<ContextEvent> = emptyList(), // already retrieved by caller
    val maxTokens: Int = 512,
    val skillHint: String? = null         // if WS4 already chose a skill
)

enum class FulfillmentMode { CLOUD, LOCAL, HYBRID }
```

## Response stream

```kotlin
sealed class FulfillmentEvent {
    data class RouteDecided(val path: ModelPath, val reason: String) : FulfillmentEvent()
    data class Token(val text: String) : FulfillmentEvent()
    data class PartialResult(val text: String) : FulfillmentEvent()   // emitted at sentence boundaries
    data class Done(val finalText: String, val telemetry: FulfillmentTelemetry) : FulfillmentEvent()
    data class Failed(val error: FulfillmentError, val telemetry: FulfillmentTelemetry) : FulfillmentEvent()
    data class FellBack(val from: ModelPath, val to: ModelPath, val reason: String) : FulfillmentEvent()
}

enum class ModelPath { CLOUD_LLM, LOCAL_LLM, INTENT_ROUTED_CLOUD, INTENT_ROUTED_LOCAL, REJECTED_BY_GATE }

data class FulfillmentTelemetry(
    val path: ModelPath,
    val routeReason: String,
    val firstTokenMs: Long?,
    val totalMs: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    val fallbackOccurred: Boolean,
    val networkUsed: Boolean
)

enum class FulfillmentError { NETWORK, MODEL_NOT_INSTALLED, OUT_OF_MEMORY, INTERRUPTED, INTERNAL }
```

## API surface

```kotlin
interface Fulfillment {
    fun fulfill(req: FulfillmentRequest): Flow<FulfillmentEvent>
    suspend fun cancel(requestId: String)
    suspend fun status(): FulfillmentStatus           // which models installed, network reachability
}
```

## Invariants

1. **Mode is honored.** `LOCAL` never makes a network call. Verified by the local-only egress test.
2. **Every stream ends in exactly one terminal event** — `Done` or `Failed`. Cancellation surfaces as `Failed(INTERRUPTED)`.
3. **`RouteDecided` is the first event** in every stream and is emitted before any `Token`.
4. **Hybrid fallback is observable.** A downgrade from cloud to local emits `FellBack` before the new path's first `Token`.
5. **Telemetry is always present in terminals**, even on failure.

## Threading / lifecycle

```text
- fulfill() returns a cold Flow; collection drives the work.
- Cancellation is cooperative via Flow cancellation OR explicit cancel(requestId).
- Single in-flight request per requestId; duplicates are rejected with Failed(INTERNAL).
```

## What this contract does NOT include

```text
- ASR. Voice input is converted to text by WS3's ASR adapter before fulfill() is called.
- Embedding retrieval. Caller (WS4) retrieves ContextEvents via ContextStore and passes them in.
- Skill dispatch. WS4 reads PartialResult/Done and decides what to do.
```

This separation keeps WS3 a pure request → answer service and prevents skill logic from leaking into the model layer.
