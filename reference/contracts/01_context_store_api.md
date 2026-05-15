# Contract 01 — ContextStore API

**Producer:** WS2
**Consumers:** WS3 (retrieval for prompts), WS4 (recall skill, summary skill), WS1 (UI surfacing of citations)
**Status:** must land Week 1. Breaking changes after M2 require a labeled "interface" PR + sign-off from every consumer.

## Purpose

Single source of truth for captured context events. Everything that writes to memory goes through this; everything that reads from memory goes through this. No direct DB access from outside WS2.

## Event schema

```kotlin
data class ContextEvent(
    val id: String,                  // ULID
    val source: Source,              // AUDIO | SCREEN | NOTIFICATION | CAMERA | INTERACTION
    val timestampMs: Long,           // epoch ms, capture time
    val text: String?,               // textual representation (transcript, OCR, body)
    val blobRef: String?,            // optional content-addressed blob path (audio/image)
    val embedding: FloatArray?,      // optional, populated by WS3 embedder
    val retentionClass: RetentionClass, // EPHEMERAL | SHORT | LONG
    val sourceMetadata: Map<String, String> // e.g. app package for notifications
)

enum class Source { AUDIO, SCREEN, NOTIFICATION, CAMERA, INTERACTION }
enum class RetentionClass { EPHEMERAL, SHORT, LONG }   // 5 min / 24 h / 30 d default
```

## API surface

```kotlin
interface ContextStore {
    // Write
    suspend fun insert(event: ContextEvent): InsertResult
    suspend fun attachEmbedding(id: String, embedding: FloatArray)

    // Read
    suspend fun queryByTime(range: LongRange, sources: Set<Source>? = null): List<ContextEvent>
    suspend fun queryByText(query: String, limit: Int = 20): List<ContextEvent>      // FTS
    suspend fun queryByEmbedding(vec: FloatArray, limit: Int = 20): List<ScoredEvent> // ANN

    // Delete
    suspend fun delete(id: String)
    suspend fun deleteRange(range: LongRange, sources: Set<Source>? = null): Int     // "forget last N min"
    suspend fun purgeSource(source: Source): Int                                      // user disabled a source

    // Observability
    fun events(): Flow<ContextEvent>          // hot stream of inserts
    suspend fun stats(): ContextStoreStats    // counts per source, retention bucket
}

sealed class InsertResult {
    object Stored : InsertResult()
    data class Rejected(val reason: RejectReason) : InsertResult()
}

enum class RejectReason { SOURCE_DISABLED, RETENTION_FULL, SCHEMA_INVALID }
data class ScoredEvent(val event: ContextEvent, val score: Float)
```

## Invariants (tested, not just documented)

1. `insert()` of an event whose `source` is currently disabled returns `Rejected(SOURCE_DISABLED)` and writes nothing. Verified by the disabled-source privacy test.
2. Retention sweep removes any event older than its `retentionClass` window on the next sweep tick.
3. `purgeSource(X)` removes every event with `source == X` and is irreversible.
4. `queryByEmbedding` returns empty when no events have embeddings, never throws.
5. Embeddings are opaque to WS2 — dimension and model identity owned by WS3.

## Threading / lifecycle

```text
- All suspend functions are main-safe; the implementation hops to IO internally.
- events() is a SharedFlow with replay = 0; subscribers must be ready before insert.
- Singleton, owned by an AndroidX Hilt module in WS2's gradle module.
```

## Error model

Functions that can fail return sealed result types (`InsertResult`). Functions that read return empty collections on no-match. Exceptions are reserved for programmer error (bad schema, DB corruption) — never thrown for ordinary "no data" cases.

## Versioning

Schema migrations owned by WS2 via Room migrations. Adding fields = non-breaking. Removing/renaming fields = breaking, requires interface PR.
