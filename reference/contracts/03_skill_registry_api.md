# Contract 03 — Skill Registry API

**Producer:** WS4
**Consumers:** WS1 (Skills discovery screen, confirmation UI), WS3 (route → skill mapping when intent classifier fires)
**Status:** must land Week 1. Skill schema is additive after M2.

## Purpose

A registry of named capabilities the assistant can perform. Decouples "what skills exist" from "how they're invoked" so WS1 can render them and WS3 can route to them without either knowing implementation details.

## Skill descriptor

```kotlin
data class SkillDescriptor(
    val id: String,                       // stable, e.g. "timer.set"
    val displayName: String,
    val category: SkillCategory,          // PRODUCTIVITY | CAPTURE | KNOWLEDGE | UTILITY
    val triggers: List<Trigger>,          // text patterns, intents
    val inputSchema: JsonSchema,          // expected slots
    val outputKind: OutputKind,           // ANSWER | DRAFT | SIDE_EFFECT
    val requiresConfirmation: Boolean,    // implied true for SIDE_EFFECT
    val requiredPermissions: Set<Permission> = emptySet(),
    val enabledByDefault: Boolean = true
)

enum class SkillCategory { PRODUCTIVITY, CAPTURE, KNOWLEDGE, UTILITY }
enum class OutputKind { ANSWER, DRAFT, SIDE_EFFECT }

sealed class Trigger {
    data class TextPattern(val regex: String) : Trigger()
    data class Intent(val name: String) : Trigger()
    data class EmbeddingExemplars(val phrases: List<String>) : Trigger()
}
```

## Invocation

```kotlin
data class SkillInvocation(
    val skillId: String,
    val args: JsonObject,                 // validated against inputSchema
    val sourceRequestId: String           // links back to the FulfillmentRequest
)

sealed class SkillResult {
    data class Answer(val text: String) : SkillResult()
    data class Draft(val title: String, val body: String, val mime: String) : SkillResult()
    data class SideEffectPending(val confirmationToken: String, val summary: String) : SkillResult()
    data class SideEffectApplied(val auditId: String, val summary: String) : SkillResult()
    data class Failed(val reason: String) : SkillResult()
}
```

## API surface

```kotlin
interface SkillRegistry {
    fun list(): List<SkillDescriptor>
    fun listByCategory(c: SkillCategory): List<SkillDescriptor>
    fun get(id: String): SkillDescriptor?

    suspend fun setEnabled(id: String, enabled: Boolean)
    fun observeEnabled(id: String): Flow<Boolean>

    suspend fun invoke(inv: SkillInvocation): SkillResult
    suspend fun confirm(token: String): SkillResult     // resolves a SideEffectPending
    suspend fun cancel(token: String)
}
```

## Invariants

1. **Confirmation is non-bypassable.** Any skill with `outputKind = SIDE_EFFECT` returns `SideEffectPending` from `invoke()`. Only `confirm(token)` produces `SideEffectApplied`.
2. **Disabled skills are unroutable.** `invoke()` on a disabled skill returns `Failed("disabled")` and is not selectable by WS3 routing.
3. **Audit trail.** Every `SideEffectApplied` has an `auditId` linking to a persisted record (skill, args, requestId, timestamp, outcome).
4. **Schema validation happens at the registry boundary.** `args` failing `inputSchema` returns `Failed("schema:<path>")` before the skill runs.
5. **Permissions are checked before invocation.** Missing required permissions return `Failed("permission:<name>")` and surface to WS1 to prompt.

## Required initial skill set (for parity)

```text
list.create
timer.set
calendar.draft
document.draft
web.search
jargon.lookup
insight.generate
recall.query
conversation.summarize
```

WS4 owns the implementation of each. Adding new skills is non-breaking.

## What this contract does NOT include

```text
- Model routing logic. WS3 decides which skill to invoke based on triggers; it calls invoke().
- UI rendering. WS1 reads SkillDescriptor + SkillResult and renders.
- Storage of drafts. Drafts are returned as SkillResult; WS1 decides where they go.
```
