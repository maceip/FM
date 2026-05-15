# Cosmo LLM Stack — Static Analysis Map

Static-analysis map of every LLM-side surface in the recovered Cosmo APK
(`com.google.research.air.cosmo`, antisplit v1.1):

- the `LLM` abstraction and its implementations,
- the on-device AICore (Gemini Nano) integration,
- the Private Aratea cloud client surface used in this build,
- the gatekeeping → fulfillment routing,
- skill authoring (jinja prompts + textproto schemas),
- action execution back to the OS.

Same artifact-only discipline as the audio/text/image docs. Every claim is
backed by a smali grep, a const-string, an ELF symbol, an `aapt2` line,
or extracted asset bytes. Where a body is stripped, the row reads `(body
not recovered)`. Where a remote service surface is opaque to this APK,
the row reads `(external; not in APK)`.

Permission tags introduced here:

| Tag | Permission | Notes |
|---|---|---|
| `[P:AICORE]` | `com.google.android.apps.aicore.service.BIND_SERVICE` | declared by Cosmo; binds to the platform AICore service |
| `[P:INTERNET]` | `android.permission.INTERNET` | declared by Cosmo |
| `[P:NET_STATE]` | `android.permission.ACCESS_NETWORK_STATE` | declared by Cosmo |
| `[P:WAKE_LOCK]` | `android.permission.WAKE_LOCK` | declared by Cosmo |

(Other permission tags are defined in the audio/text/image docs.)

---

## Section A — `LLM` interface and implementations

### A.1 `com.google.research.air.cosmo.lib.llm.LLM`

```
public abstract generateText(java.lang.String, Continuation): Object
public abstract inference(java.lang.String, kotlin.jvm.functions.Function1, Continuation): Object
public abstract inference(java.util.List, kotlin.jvm.functions.Function1, Continuation): Object
```

Sibling smali file `LLM$generateText$1.smali` exists; its body extends a
default-method coroutine continuation for the `String→Object`
generateText call.

### A.2 `com.google.research.air.cosmo.lib.llm.PromptPart` (sealed class)

Files in `com/google/research/air/cosmo/lib/llm/`:

```
PromptPart.smali
PromptPart$Audio.smali
PromptPart$Image.smali
PromptPart$Text.smali
```

`PromptPart`:

```
private constructor <init>()
public synthetic constructor <init>(kotlin.jvm.internal.DefaultConstructorMarker)
```

`PromptPart$Audio` fields:

```
private final audioBytes: com.google.protobuf.ByteString
private final mimeType: java.lang.String
```

`PromptPart$Image` fields:

```
private final imageBytes: byte[]
private final mimeType: java.lang.String
```

`PromptPart$Text` fields:

```
private final text: java.lang.String
```

(Each subclass exposes the Kotlin data-class accessors `getAudioBytes()`,
`getMimeType()`, `getImageBytes()`, `getText()`, plus `component1`,
`copy`, `equals`, `hashCode`, `toString`.)

### A.3 `com.google.research.air.cosmo.lib.llm.AICoreLLM`

Constructor:

```
public <init>(android.content.Context)
```

Methods:

```
public inference(java.lang.String, Function1, Continuation): Object
public inference(java.util.List, Function1, Continuation): Object
private final init(Continuation): Object
```

External invokes (deduped, AICore-relevant, from `invoke-…` grep):

```
com.google.android.apps.aicore.client.api.AiCoreClient.listFeatures()
com.google.android.apps.aicore.client.api.AiCoreClientOptions.builder()
com.google.android.apps.aicore.client.api.AiCoreClientOptions$Builder.build()
com.google.android.apps.aicore.client.api.AiFeature.getId()
com.google.android.apps.aicore.client.api.AiFeature.getType()
com.google.android.apps.aicore.client.api.llm.LlmMessage.create(...)
com.google.android.apps.aicore.client.api.llm.LlmReply.getText()
com.google.android.apps.aicore.client.api.llm.LlmRequest.builder()
com.google.android.apps.aicore.client.api.llm.LlmRequest$Builder.build()
com.google.android.apps.aicore.client.api.llm.LlmRequest$Builder.setMessages(...)
com.google.android.apps.aicore.client.api.llm.LlmRequest$Builder.setStreamingCallback(...)
com.google.android.apps.aicore.client.api.llm.LlmRequest$Builder.setTargetReplyLengthMax(...)
com.google.android.apps.aicore.client.api.llm.LlmResult.getResults()
com.google.android.apps.aicore.client.api.llm.LlmService.create(...)
com.google.android.apps.aicore.client.api.llm.LlmService.runInference(...)
com.google.android.apps.aicore.client.api.llm.LlmServiceOptions.builder()
com.google.android.apps.aicore.client.api.llm.LlmServiceOptions$Builder.build()
com.google.android.apps.aicore.client.api.llm.LlmServiceOptions$Builder.setFeature(AiFeature)
com.google.research.air.cosmo.lib.llm.PromptPart$Text.getText()
```

const-strings observed in `AICoreLLM.smali`:

```
"AICore inference failed."
"AiFeature 108 (LLM-Nano V3) not found."
"Complete AiCore response: %s."
"Failed to get AiCore llmService."
"COSMO-AICoreLLM"
"aiCoreClient"
```

The numeric AiFeature `108 (LLM-Nano V3)` is the Cosmo-side AICore feature
ID used to bind to Google Nano on the device. The class fails-safe with
the `"AiFeature 108 (LLM-Nano V3) not found."` log if Nano isn't
available. (`com.google.android.apps.aicore.service.BIND_SERVICE` is
declared in Cosmo's manifest.)

[P:AICORE]. [R:OK on Pixels with AICore + Nano installed]; [R:BLOCKED on
devices without `com.google.android.apps.aicore`].

### A.4 `com.google.research.air.cosmo.lib.llm.LTCLLM`

Methods:

```
public <init>()
public inference(java.lang.String, Function1, Continuation): Object
```

(Sibling files `LTCLLM$Companion.smali` and `LTCLLM$inference$2.smali`
exist; the per-method body is not enumerated. The `<init>()` constructor
takes no args. No `^const-string` in this file references a service
binding or external API.)

### A.5 `com.google.research.air.cosmo.lib.llm.LocalLLM`

`^\.method` grep returns no public methods in this file. (The smali file
exists but contains only inherited members. Body not recovered.)

### A.6 `com.google.research.air.cosmo.lib.llm.HybridLLM`

`^\.method` grep returns no public methods. (Body not recovered.)

### A.7 `com.google.research.air.cosmo.lib.llm.NetworkAwareLLM`

Constructor:

```
public <init>(LLM, LLM, android.content.Context)
```

Methods:

```
public inference(java.lang.String, Function1, Continuation): Object
public inference(java.util.List, Function1, Continuation): Object
private final getActiveLlm(): LLM
```

The two `LLM` constructor parameters: the class holds two delegates.
The `inference(...)` methods (verified by `invoke-…` grep on
`NetworkAwareLLM.smali`) call:

```
com.google.research.air.cosmo.lib.util.NetworkUtilsKt.isNetworkAvailable(Context): boolean
```

…and `NetworkUtilsKt.isNetworkAvailable` (verified by grep on its smali)
calls:

```
android.net.ConnectivityManager.getActiveNetwork()
android.net.ConnectivityManager.getNetworkCapabilities(Network)
```

[P:NET_STATE] is therefore reached via this concrete call chain.

### A.8 `com.google.research.air.cosmo.lib.llm.ServerLLM`

`^\.method` grep returns no public methods. (Body not recovered.)

### A.9 `com.google.research.air.cosmo.lib.llm.PrivateInferenceArateaLLM`

Constructor:

```
public <init>(com.google.android.libraries.mdi.privatearatea.PrivateArateaClient,
              com.google.research.air.cosmo.lib.llm.LLMStatsRepository)
```

Methods:

```
public inference(java.lang.String, Function1, Continuation): Object
public inference(java.util.List, Function1, Continuation): Object
private final buildPrivateArateaRequest(java.util.List, boolean):
        com.google.search.mdi.privatearatea.proto.PrivateArateaRequest
private final getRequestMetadata():
        com.google.android.apps.miphone.astrea.privateinference.library
            .PrivateInferenceRequestMetadata
private final promptPartsToDebugString(java.util.List): java.lang.String
private final tryInference(java.util.List, boolean, Continuation): Object
private static final requestMetadata_delegate$lambda$0():
        PrivateInferenceRequestMetadata
private static final requestMetadata_delegate$lambda$0$0(
        PrivateInferenceRequestMetadata$AuthInfo): PrivateInferenceRequestMetadata$AuthInfo
```

External invokes (deduped, Aratea-relevant):

```
androidx.concurrent.futures.ListenableFutureKt.await(ListenableFuture, Continuation)
com.google.android.apps.miphone.astrea.privateinference.library.PrivateInferenceRequestMetadata$AuthInfo.apiKey()
com.google.android.libraries.mdi.privatearatea.PrivateArateaClient.performInference(PrivateArateaRequest, …)
com.google.research.air.cosmo.lib.llm.LLMStatsRepository.incrementFailure()
com.google.research.air.cosmo.lib.llm.LLMStatsRepository.incrementHit()
com.google.research.air.cosmo.lib.llm.LLMStatsRepository.incrementSuccess()
com.google.research.air.cosmo.lib.llm.LLMStatsRepository.updatePrivateInferenceEnabled(boolean)
com.google.research.air.cosmo.lib.llm.PromptPart$Audio.getAudioBytes()
com.google.research.air.cosmo.lib.llm.PromptPart$Audio.getMimeType()
com.google.research.air.cosmo.lib.llm.PromptPart$Image.getImageBytes()
com.google.research.air.cosmo.lib.llm.PromptPart$Image.getMimeType()
com.google.research.air.cosmo.lib.llm.PromptPart$Text.getText()
com.google.search.mdi.privatearatea.proto.Blob.newBuilder()
com.google.search.mdi.privatearatea.proto.BlobKt$Dsl.setData(...)
com.google.search.mdi.privatearatea.proto.BlobKt$Dsl.setMimeType(...)
com.google.search.mdi.privatearatea.proto.Candidate.getContent()
com.google.search.mdi.privatearatea.proto.Content.getPartsList()
com.google.search.mdi.privatearatea.proto.Content.newBuilder()
```

[P:INTERNET] (transitively, via the Aratea client's network round-trip).

### A.10 `com.google.research.air.cosmo.lib.llm.PrivateInferenceArateaBatchLLM`

Constructor:

```
public <init>(com.google.android.libraries.mdi.privatearatea.client.PrivateArateaClientFactory)
```

Methods:

```
public runBatchInference(java.lang.String,
                         PromptPart$Audio,
                         java.util.List,
                         Continuation): Object
private final buildPrivateArateaRequest(java.lang.String,
                                         PromptPart$Audio,
                                         PromptPart$Audio):
        com.google.search.mdi.privatearatea.proto.PrivateArateaRequest
private final getPrivateArateaBatchClient():
        com.google.android.libraries.mdi.privatearatea.client.PrivateArateaClient
private final getRequestMetadata():
        com.google.android.apps.miphone.astrea.privateinference.library
            .PrivateInferenceRequestMetadata
private final runInference(java.lang.String,
                           PromptPart$Audio,
                           PromptPart$Audio,
                           Continuation): Object
private static final privateArateaBatchClient_delegate$lambda$0(
        PrivateInferenceArateaBatchLLM):
        com.google.android.libraries.mdi.privatearatea.client.PrivateArateaClientImpl
```

Implements `BatchInferenceLLM` (interface):

```
public abstract runBatchInference(java.lang.String,
                                   PromptPart$Audio,
                                   java.util.List,
                                   Continuation): Object
```

### A.11 `com.google.research.air.cosmo.lib.llm.LLMStatsRepository`

```
public <init>()
public final getInferenceStats(): kotlinx.coroutines.flow.StateFlow
public final getRecentSuccessCount(): kotlinx.coroutines.flow.StateFlow
public final getRecentTotalCount(): kotlinx.coroutines.flow.StateFlow
public final getStartTime(): kotlinx.coroutines.flow.StateFlow
public final isPrivateInferenceEnabled(): kotlinx.coroutines.flow.StateFlow
public final declared-synchronized incrementFailure(): void
public final incrementHit(): void
public final declared-synchronized incrementSuccess(): void
public final declared-synchronized reset(): void
public final declared-synchronized updatePrivateInferenceEnabled(boolean): void
private final addRecentCall(boolean): void
```

`LLMInferenceStats` fields:

```
private final hit:     int
private final success: int
private final failure: int
```

### A.12 `com.google.research.air.cosmo.lib.llm.ArateaExecutorModule`

Hilt module (interface). const-strings:

```
"bindBackgroundExecutor"
"bindLightweightExecutor"
"executor"
"Lcom/google/common/util/concurrent/ListeningExecutorService;"
```

Two abstract `@Bind` methods that return `ListeningExecutorService`
qualifiers — `BackgroundExecutor` and `LightweightExecutor`. The bodies
are abstract.

### A.13 `com.google.research.air.cosmo.lib.llm.LLMModule`

Hilt module file present (smali). const-strings / methods not enumerated.

---

## Section B — Native libraries that participate in the LLM path

### B.1 `libpi_client_session_config_jni.so`

Size: 2,130,536 bytes.
ELF NEEDED (from `cosmo_baksmali_imports/native_libraries.md`):
`libdl.so liblog.so libc.so libm.so`.

JNI export count and names not enumerated in this analysis; the
`native_libraries.md` summary describes the role as
"Private Inference / Oak async client session config JNI."

Cosmo references it via the
`com.google.android.libraries.mdi.privatearatea.*` packages used by
`PrivateInferenceArateaLLM` and `PrivateInferenceArateaBatchLLM`
(§A.9–A.10).

### B.2 `liboak_session_config_builder_jni.so`

Size: 86,176 bytes.
ELF NEEDED: `libdl.so libc.so libm.so`.

`native_libraries.md` describes it as "Oak session config builder JNI".
This is the Oak attestation/session library used to set up encrypted
sessions to remote enclaves; Aratea uses Oak for the session-encryption
layer per the public Aratea protobuf (`google/private-compute-services` —
external to this APK; not in scope for this doc).

### B.3 No AICore-side JNI in the bundled `.so` set

`nm -D` on every `.so` in `cosmo_recovered_native_libs/lib/arm64-v8a/`
returns zero `T Java_*aicore*` exports (verified — the full lib set is
`libandroidx.graphics.path.so`, `libbert_gatekeeper_jni.so`,
`libdarwinndelegatejni.so`, `libfilterframework_jni.so`,
`libimage_processing_util_jni.so`, `liboak_session_config_builder_jni.so`,
`libpi_client_session_config_jni.so`, `libsbcdecoder_jni.so`,
`libsoda_dev_jni.so`, `libsurface_util_jni.so`,
`libtensorflowlite_jni.so`, `libtensorflowlite_jni_stable.so`,
`libvogo_jni.so`).

AICore is reached only via the Java
`com.google.android.apps.aicore.client.api` surface (§A.3). The
on-device Nano weight-loading and inference happen in the platform
`com.google.android.apps.aicore` process; that process is external
to this APK.

---

## Section C — Skill authoring

### C.1 `com.google.research.air.cosmo.skills.generation.SkillGenerator`

Constructor:

```
public <init>(LLM, com.google.research.air.cosmo.skills.generation.PromptBuilder)
```

Methods:

```
public generateSkill-gIAlu-s(java.lang.String, Continuation): Object
private final parseSkillGenerationResponse(java.lang.String):
        com.google.research.air.cosmo.skills.Skill
private static final parseSkillGenerationResponse$lambda$0(java.lang.Object): java.lang.String
private static final parseSkillGenerationResponse$lambda$1(java.lang.Object): ActionInfo
private static final parseSkillGenerationResponse$lambda$2(java.lang.Object):
        com.google.research.air.cosmo.agents.proactivesuggestions.ContextSource
```

const-strings observed:

```
"promptBuilder"
"SkillGenerator prompt:\n%s"
```

### C.2 `com.google.research.air.cosmo.skills.generation.GatekeepingGenerator`

Constructor:

```
public <init>(LLM, android.content.Context, PromptBuilder)
```

Methods:

```
public generateGatekeeperTriggerInfo-0E7RQCE(java.lang.String,
                                              java.lang.String,
                                              Continuation): Object
private final parseGatekeeperTriggerInfoResponse(java.lang.String):
        com.google.research.air.cosmo.skills.GatekeeperTriggerInfo
private static final parseGatekeeperTriggerInfoResponse$lambda$0(java.lang.Object): java.lang.String
private static final parseGatekeeperTriggerInfoResponse$lambda$1(java.lang.Object): java.lang.String
```

### C.3 `com.google.research.air.cosmo.skills.generation.PromptBuilder`

`.super Ljava/lang/Object;` (verified). Constructor:

```
public <init>(android.content.Context)
```

Public methods (`^\.method` grep, non-synthetic):

```
public final buildGatekeeperTriggerPrompt(java.lang.String, java.lang.String): java.lang.String
public final buildSkillDefinitionPrompt(java.lang.String): java.lang.String
public final getActionTypeDescription$java_…(): java.lang.String
public final getContextSourceDescription$java_…(): java.lang.String
public static synthetic getActionTypeDescription$…$annotations(): void
public static synthetic getContextSourceDescription$…$annotations(): void
```

`^const-string ` grep on this file includes the asset/template-key
strings:

```
"gatekeeper_trigger_prompt.jinja2"
"skill_definition_prompt.jinja2"
"gatekeeper_trigger_info_schema"
"filtered_skill_schema"
".textproto"
"actions_descriptions"
"context_source_description"
"fulfillment_instruction"
"skill_description"
"skillDescription"
"fulfillmentInstruction"
"context"
```

…plus the source-tag literals shown to the LLM:

```
"ambient audio"
"buds audio"
"device audio"
"device camera"
"glasses camera"
"intent"
"notification"
```

…and the verbatim per-action descriptions used to expand
`{{ actions_descriptions }}` in the prompts (sample, verified):

```
"Add a task to Google Tasks. Use this skill when the user wants to add a task
 to their to-do list, schedule a reminder, or organize their tasks."

"Add items to a shopping, to-do, or other specific list. Use this skill when
 the user wants to remember items to buy, tasks to complete, or things to pack,
 ensuring items are added to the most relevant list context."

"Conduct in-depth research on complex topics. Use this skill when the user
 asks extensive questions that require synthesizing information from multiple
 sources, deep analysis, or a comprehensive report."

"Create a new event in the user's calendar. Use this skill when the user
 confirms a specific event with a date and time, such as a meeting,
 appointment, or social gathering."

"Create a new Google Doc with an optional title and initial content. Use this
 skill when the user wants to draft a document, report, or note, providing a
 starting point or a full draft based on context."

"Create a timer for a specific duration. Use this skill when the user
 explicitly asks to set a timer or implies a need to track a specific
 duration of time, such as for cooking, activities, or reminders."

"Define a specific term or concept. Use this skill when the user explicitly
 asks for a definition or expresses confusion about a term, technical jargon
 or abbreviation."

"Delegate complex workflows, multi-step planning, cross-app tasks, or
 proactive automations to AgentQ. AgentQ is an autonomous agent with deep OS
 integration that can control Core OS & Utilities (Camera, Clock, Contacts,
 File Manager, Flashlight, Keyboard & Launcher, Voice Recorder), Productivity
 & Work (Calendar, Notes, Calculator, Google Drive, GitHub, Slack),
 Communication & Social (SMS, Telegram), Media & Entertainment (Gallery,
 Music Player, Spotify, YouTube), and Travel & Information apps (Weather,
 Caltrain, TfL) without manual user intervention. It can also set up
 background routines triggered by time or specific notifications. Trigger
 this skill especially for cross-app coordination, complex reasoning, or
 establishing proactive routines."

"Draft an email on behalf of the user. Use this skill when the user wants to
 compose and send an email, with the recipient (optional), subject, and body
 generated based on user context and query or conversation."

"Draft an SMS message to a specific recipient. Use this skill when the user
 wants to send a text message to someone, including the message content."

"Execute an ActiveData multi-step plan. Use this skill when the user wants
 to run a sequence of actions or a workflow generated to fulfill a complex
 request."

"Find locations, get directions and navigate to locations using Google Maps.
 Use this skill when the user is asking for directions, looking for a place,
 or needs help navigating to a destination."

"Initiate a phone call to a business or service. Use this skill when the
 user wants to make a reservation, schedule an appointment, or contact a
 business directly, and a phone call is the most appropriate method."

"Offer analysis, insight, or clarification. Use this skill when the
 conversation benefits from a deeper explanation, a new perspective, or a
 synthesis of the current topic to advance the dialogue."

"Perform a complex, multi-step web tasks on the user's behalf. Use this
 skill when the user needs assistance with actions that require navigating
 multiple pages, interacting with forms, or synthesizing information from
 various web sources to achieve a specific goal."

"Perform a targeted web search. Use this skill to answer specific factual
 questions, resolve confusion, or retrieve up-to-date information when the
 user's intent is clear but requires external knowledge."

"Play music based on explicit user requests, or implicitly by detecting
 conversational cues about music preferences (songs, artists, genres), or
 by sensing the ambient mood or context of a situation."
```

(Additional descriptions for `add_to_memory`, `provide_event_context`,
`provide_insight`, `provide_person_context`, `provide_recall`,
`query_photos` etc. exist in the same `^const-string ` set; sample above
is representative but not exhaustive.)

### C.4 Skill protobuf `FIELD_NUMBER` constants

`com.google.research.air.cosmo.skills.Skill`:

```
ID_FIELD_NUMBER                       = 0x1
DESCRIPTION_FIELD_NUMBER               = 0x3
FULFILLMENT_INSTRUCTION_FIELD_NUMBER   = 0x4
ACTIONS_FIELD_NUMBER                   = 0x5
SENSITIVITY_FIELD_NUMBER               = 0x6
SOURCES_FIELD_NUMBER                   = 0x7
ADD_TO_MEMORY_FIELD_NUMBER             = 0x8
GATEKEEPER_TRIGGER_INFO_FIELD_NUMBER   = 0x9
ICON_FIELD_NUMBER                      = 0xa
STOREFRONT_MESSAGE_FIELD_NUMBER        = 0xc
```

### C.5 `ContextSource` enum (from `proactivesuggestions/ContextSource.smali`)

```
CONTEXT_SOURCE_UNSPECIFIED
CONTEXT_SOURCE_AMBIENT_AUDIO
CONTEXT_SOURCE_BUDS_AUDIO
CONTEXT_SOURCE_DEVICE_AUDIO
CONTEXT_SOURCE_WATCH_AUDIO
CONTEXT_SOURCE_DEVICE_CAMERA
CONTEXT_SOURCE_GLASSES_CAMERA
CONTEXT_SOURCE_NOTIFICATION
CONTEXT_SOURCE_SCREEN
CONTEXT_SOURCE_INTENT
UNRECOGNIZED
```

### C.6 The skill schema asset

`assets/filtered_skill_schema.textproto` is 6,348 bytes. Verbatim excerpt
of the action enum (extracted via `unzip -p`):

```
key: "action_type"
value {
  type: STRING
  description: "The type of action to be suggested/performed."
  enum: "ACTION_TYPE_UNKNOWN"
  enum: "ACTION_TYPE_ADD_TO_MEMORY"
  enum: "ACTION_TYPE_ADD_TO_LIST"
  enum: "ACTION_TYPE_CREATE_CALENDAR_EVENT"
  enum: "ACTION_TYPE_WEB_SEARCH"
  enum: "ACTION_TYPE_DEFINE_TERM"
  enum: "ACTION_TYPE_QUERY_PHOTOS"
  enum: "ACTION_TYPE_CREATE_DOC"
  enum: "ACTION_TYPE_PROVIDE_PERSON_CONTEXT"
  enum: "ACTION_TYPE_PROVIDE_EVENT_CONTEXT"
  enum: "ACTION_TYPE_DEEP_RESEARCH"
  enum: "ACTION_TYPE_PROVIDE_INSIGHT"
  enum: "ACTION_TYPE_BROWSING_AGENT"
  enum: "ACTION_TYPE_ADD_TIMER"
  enum: "ACTION_TYPE_CALL_AGENT"
  enum: "ACTION_TYPE_PROVIDE_RECALL"
  enum: "ACTION_TYPE_NAVIGATION"
  enum: "ACTION_TYPE_INVOKE_AGENT_Q"
  enum: "ACTION_TYPE_DRAFT_EMAIL"
  enum: "ACTION_TYPE_ADD_TASK"
  enum: "ACTION_TYPE_DRAFT_SMS"
  enum: "ACTION_TYPE_PLAY_MUSIC"
  enum: "ACTION_TYPE_ACTIVE_DATA_PLAN"
}
```

The same schema also defines the `Skill` top-level fields: `actions`,
`add_to_memory`, `description`, `fulfillment_instruction`, `icon`,
`sensitivity`, `sources`, `storefront_message`, `tags`, `title` — all
mapped to JSON-Schema-compatible types. Property ordering is also
declared in the textproto (sequence: title, description,
fulfillment_instruction, actions, sensitivity, sources, add_to_memory,
icon, tags, storefront_message).

### C.7 The gatekeeper-trigger schema asset

`assets/gatekeeper_trigger_info_schema.textproto` is 801 bytes. Full
text:

```
type: OBJECT
description: "Defines the Gatekeeper triggering logic for a skill."
properties {
  key: "trigger_descriptions"
  value {
    type: ARRAY
    description: "One or more descriptions of what to look for, e.g. \"When I discuss soccer\". The gatekeeper uses it to generate \"skill embeddings\"."
    items { type: STRING }
    default { list_value { } }
  }
}
properties {
  key: "trigger_strings"
  value {
    type: ARRAY
    description: "Triggering regexes to support recall of the gatekeeper. The skill should trigger if any of these regexes match the context."
    items { type: STRING }
    default { list_value { } }
  }
}
property_ordering: "trigger_descriptions"
property_ordering: "trigger_strings"
title: "GatekeeperTriggerInfo"
```

---

## Section D — Jinja prompt assets

All prompt files extracted via `unzip -p` from the antisplit APK.

| Asset | Bytes | Loaded by (smali grep on `^const-string ` matching the filename) |
|---|---|---|
| `assets/intent_extraction_prompt.jinja2` | 1,942 | `IntentExtractorImpl` |
| `assets/gatekeeper_trigger_prompt.jinja2` | 6,064 | `skills/generation/PromptBuilder` |
| `assets/skill_definition_prompt.jinja2` | 6,086 | `skills/generation/PromptBuilder` |
| `assets/fulfillment_prompt.jinja2` | 15,417 | `fulfillment/prompts/PromptBuilder` |
| `assets/reactive_fulfillment_prompt.jinja2` | 14,054 | `fulfillment/prompts/PromptBuilder` |
| `assets/memory_retrieval_prompt.jinja2` | 1,775 | `fulfillment/prompts/PromptBuilder` |
| `assets/gboard_prompt.jinja2` | 820 | `gboard/prompt/PromptGenerator` |

(The "Loaded by" column is established by `^const-string ` grep across
the whole smali tree for the filename literal.)

The full `intent_extraction_prompt.jinja2` text is reproduced in
`text_stack_map.md` §E.3.

### D.1 `gatekeeper_trigger_prompt.jinja2` (excerpts, verbatim)

The prompt declares its data sources (verbatim header excerpt):

> The Gatekeeper processes **text-based snippets** from the following
> sources. It does not see or hear noises, music, images, colors, or
> icons:
>   * Ambient Conversation: Transcribed speech from the user's environment.
>   * Device/Watch Audio: Transcribed text from podcasts, videos, or
>     calls playing on the device.
>   * On-Screen Text: Text extracted (via OCR or Accessibility) from the
>     active Android screen.
>   * Notifications: The text content of incoming app notifications.

Jinja placeholders used:

```
{{ actions_descriptions }}
{{ gatekeeper_trigger_info_schema }}
{{ skill_description }}
{{ fulfillment_instruction }}
```

The output instructions specify the exact JSON shape the LLM must
return:

```json
{
  "trigger_descriptions": [ ... ],
  "trigger_strings":      [ ... ]
}
```

### D.2 `skill_definition_prompt.jinja2` (excerpts, verbatim)

Placeholders used:

```
{{ skill_schema }}
{{ actions_descriptions }}
{{ context_source_description }}
```

Tag list in the prompt (verbatim, from `skill_definition_prompt.jinja2`):

```
- Productivity
- Information
- Conversation
- Automation
- Memory
- Wellness
- Home
- Media
- Research
- Scheduling
```

### D.3 `fulfillment_prompt.jinja2` (excerpts, verbatim)

Pydoc Args header:

```
Args:
    memories                  (list)
    previous_suggestions      (list)
    executed_suggestions      (list)
    userDismissed_suggestions (list)
    cosmoRemoved_suggestions  (list)
    current_date_and_time     (str)
    action_schemas            (str)
    skills                    (list)
    voice_match_enabled       (bool)
    is_double_tap             (bool)
```

Verbatim instructions header:

> You are an AI assistant managing actions based on recent mobile activity.
> You will receive:
> -   Recent Audio Memory: Text transcriptions of recent ambient mic audio.
> -   Recent Screen Memory: Text descriptions of recent screen content.
> -   Recent intents: Text descriptions of the user's recent activities and intents.
> -   Recent Notifications: Text from recent system and application notifications.
> -   Recent Raw Audio: Raw audio data from recent ambient mic audio.
> -   Currently Suggested Actions: A list of actions previously suggested.
> -   Recently Executed Actions: A list of actions recently executed.
> -   Recently Dismissed Actions: A list of actions recently dismissed.
> -   Current Date and Time: Current date and time with the day of the week.
> -   Removed Actions: A list of actions removed by you as they were no longer relevant.

Verbatim default-to-silence directive:

> CRITICAL: DEFAULT TO SILENCE (OUTPUT `[]`).
> The presence of an action schema is a capability, not an invitation to
> intervene. You must maintain a near-perfect precision threshold. Only
> generate an action if there is a clear "Service Gap": the user has a
> specific, immediate need but has NOT yet started the task and is NOT
> currently in the app required to do it.

Output is a JSON array of action edits with status `NEW` / `UPDATED` /
`REMOVED`.

### D.4 `reactive_fulfillment_prompt.jinja2`

Same input shape as `fulfillment_prompt.jinja2` plus:

```
Args:
  user_query           (str)
  conversation_history (str)
```

Verbatim CRITICAL directive:

> CRITICAL: Fulfill the User Query. This is your absolute highest
> priority.
> - If the user query is asking for an action (e.g. "create a doc", "take
>   a note", "remind me to...", "search for..."), you MUST output
>   corresponding `NEW` action(s).

### D.5 `memory_retrieval_prompt.jinja2` (verbatim)

```
Args:
  max_queries_to_generate   (int)
  ephemeral_buffer_prompt   (str)
```

Excerpt:

> You are an expert at formulating search queries. Your task is to
> generate up to {{ max_queries_to_generate }} distinct search queries
> for a semantic vector database. The queries should be designed to
> retrieve long-term memories that are relevant to the user's recent
> activity.

### D.6 `gboard_prompt.jinja2` (verbatim, full text)

> You are a proactive Assistant for a user's keyboard.
> You have access to the user's recent audio conversations as well as the
> recent screens they have seen.
> A user has specified a partial query on their keyboard, and your job is
> to infer the full query the user wants to type, expanding the query
> based on the audio conversations and recent screens.
>
> The user has opened their keyboard on the following screen:
> `{{ last_screen }}`
> On this screen, the user has typed the following partial query:
> `{{ user_query }}`
>
> The full series of screens is the following:
> ```
> <screen>.
>   {{ screen_description }}
> </screen>
> ```
> The recent audio conversation in background is:
> ```
> <audio>
>   {{ audio_description }}
> </audio>.
> ```
>
> Now, based on all of the above information, output the full query the
> user wants to type without including {{ user_query }}:

(The four placeholders are `last_screen`, `user_query`,
`screen_description`, `audio_description`.)

---

## Section E — Fulfillment orchestration

### E.1 `com.google.research.air.cosmo.fulfillment.FulfillmentOrchestrator` (interface)

```
public abstract handleBatchRun(Continuation): Object
public abstract handleDoubleTap(Continuation): Object
public abstract handleUserQuery(java.lang.String, java.lang.String, Continuation): Object
public abstract handleWatchDoublePinch(Continuation): Object
```

### E.2 `com.google.research.air.cosmo.fulfillment.FulfillmentOrchestratorImpl`

Constructor:

```
public <init>(
    com.google.research.air.cosmo.fulfillment.FulfillmentProvider,
    com.google.research.air.cosmo.fulfillment.FulfillmentConfigRegistry,
    kotlinx.coroutines.flow.SharedFlow,                                   // gatekeeper output
    kotlinx.coroutines.CoroutineScope,
    com.google.research.air.cosmo.logging.FirebaseAnalyticsWrapper,
    com.google.research.air.cosmo.fulfillment.ActionsRepository,
    com.google.research.air.cosmo.gatekeeping.util.GatekeeperResultUtils,
    com.google.research.air.cosmo.setting.CosmoSettingStore,
    com.google.research.air.cosmo.setting.CosmoConfiguration,
    com.google.research.air.cosmo.fulfillment.FulfillmentActionHandler,
    android.content.Context
)
```

Methods:

```
public handleBatchRun(Continuation): Object
public handleDoubleTap(Continuation): Object
public handleUserQuery(java.lang.String, java.lang.String, Continuation): Object
public handleWatchDoublePinch(Continuation): Object
private final doFulfillment(FulfillmentTriggerType,
                            java.lang.String,
                            java.lang.String,
                            java.lang.String,
                            Continuation): Object
private final enqueueTask(FulfillmentTask, Continuation): Object
private final getActionsFromSkills(java.util.List): java.util.Set
private final logFulfillmentRun(FulfillmentTriggerType,
                                FulfillmentResult,
                                long): void
private final startFulfillmentConsumer(Continuation): Object
private final startGatekeeperListener(Continuation): Object
private final toJson(java.util.List): java.lang.String
```

### E.3 `com.google.research.air.cosmo.fulfillment.FulfillmentTriggerType` (enum)

```
BATCH_PIPELINE
GATEKEEPER_TRIGGER
PIXEL_PHONE_DOUBLE_TAP
PIXEL_PHONE_PICK_UP
PIXEL_WATCH_DOUBLE_PINCH
USER_QUERY
```

### E.4 `com.google.research.air.cosmo.fulfillment.FulfillmentRunner`

Constructor (16 EphemeralBufferReader / Map / executor / LLM args; verbatim):

```
public <init>(
    EphemeralBufferReader × 9,                              // one per source class
    kotlinx.coroutines.CoroutineScope,
    ActionsRepository,
    com.google.research.air.cosmo.memory.MemoryStorage,
    com.google.research.air.cosmo.lib.llm.LLM,              // primary LLM
    com.google.research.air.cosmo.lib.llm.LLM,              // alternate LLM
    com.google.research.air.cosmo.setting.CosmoSettingStore,
    com.google.research.air.cosmo.fulfillment.prompts.PromptBuilder
)
```

Methods (selection):

```
public fulfill-yxL6bBk(FulfillmentConfig, java.lang.String, FulfillmentTriggerType, java.lang.String, Continuation): Object
public onAction(FulfillmentAction, Continuation): Object

public final buildEphemeralBufferPromptPart$java_com_google_research_air_cosmo_fulfillment_fulfillment_runner_impl(
        java.lang.String, java.lang.String, java.lang.String,
        ImmutableList, ImmutableList, ImmutableList, java.lang.String): java.util.List
public final camelToSnakeCase$java_com_google_research_air_cosmo_fulfillment_fulfillment_runner_impl(java.lang.String): java.lang.String
public final snakeToPascalCase$java_com_google_research_air_cosmo_fulfillment_fulfillment_runner_impl(java.lang.String): java.lang.String
public final parseResponse$java_com_google_research_air_cosmo_fulfillment_fulfillment_runner_impl(java.lang.String, java.util.Set): java.util.List
public final parseSchema$java_com_google_research_air_cosmo_fulfillment_fulfillment_runner_impl(com.google.ai.generativelanguage.v1main.Schema): FulfillmentRunner$ParseSchemaResult

private final calculatePromptPartsSizeBytes(java.util.List): long
private final extractJsonArrayString(java.lang.String): org.json.JSONArray
private final generateShortId(int): java.lang.String
private final generatedActionSchemas(java.util.List): com.google.ai.generativelanguage.v1main.Schema
private final getContentUnderLimit(java.util.List, int, long): kotlin.Pair
private final getEphemeralBufferContent(java.util.Set, int, int, Continuation): Object
private final getRecentRawAudioContent(java.util.Set, int): ImmutableList
private final loadActionSchema(): com.google.ai.generativelanguage.v1main.Schema
private final mergeSimilarActionContent(java.util.List): java.util.List
private final parseInstantParameter(java.lang.String): j$.time.Instant
private final parseParameter(kotlin.reflect.KParameter, org.json.JSONObject): java.lang.Object
private final promptPartsToDebugString(java.util.List): java.lang.String
private final retrieveMemories(java.lang.String, int, int, boolean, Function1, Function1, Continuation): Object
private final timeit(java.lang.String, Function1, Continuation): Object
private final toAnnotatedString(TimestampedAndTagged): java.lang.String
```

`com.google.ai.generativelanguage.v1main.Schema` is the return type used
for `loadActionSchema()` and `parseSchema(...)` — Cosmo uses the public
Gemini Generative Language API's `Schema` proto for action-schema
serialization (matches the `action_schemas` arg in
`fulfillment_prompt.jinja2`).

### E.5 `com.google.research.air.cosmo.fulfillment.prompts.PromptBuilder`

Constructor:

```
public <init>(android.content.Context)
```

Methods:

```
public final buildFulfillmentPromptParts(
        java.util.List,                              // memories
        java.util.List,                              // previous suggestions
        java.util.List,                              // executed suggestions
        java.util.List,                              // user-dismissed suggestions
        java.util.List,                              // cosmo-removed suggestions
        java.util.List,                              // skills
        com.google.ai.generativelanguage.v1main.Schema,   // action schemas
        java.util.List,                              // raw audio entries
        java.lang.String,                            // current_date_and_time
        java.lang.String,                            // user query (or null for non-reactive)
        boolean,                                     // voice_match_enabled
        com.google.research.air.cosmo.fulfillment.FulfillmentTriggerType,
        java.lang.String                             // conversation history
): com.google.common.collect.ImmutableList
public final buildMemoryRetrievalPrompt(java.lang.String, int): java.lang.String
private final actionSchemasToString(java.lang.String): java.lang.String
private final renderTemplate(java.lang.String, java.util.Map): java.lang.String
private final toJson(java.util.List): java.lang.String
```

const-strings observed (selection from earlier work + this analysis):

```
"fulfillment_prompt.jinja2"
"reactive_fulfillment_prompt.jinja2"
"memory_retrieval_prompt.jinja2"
"action_schemas"
"action_name"
"actionSchemas"
"context"
"conversation_history"
"current_date_and_time"
"currentDateTimeWithDayOfWeek"
"ephemeral_buffer_prompt"
"ephemeralBufferPrompt"
"executed_suggestions"
"executedActions"
"is_double_tap"
"max_queries_to_generate"
"memories"
"PLACEHOLDER_FOR_EB"
"previous_suggestions"
"previouslySuggestedActions"
"cosmoRemoved_suggestions"
"cosmoRemovedActions"
```

---

## Section F — Action execution back to the OS

### F.1 `com.google.research.air.cosmo.fulfillment.FulfillmentAction` (sealed class)

Direct sealed-class subtypes — i.e. inner-class smali files whose
`.super` line is
`Lcom/google/research/air/cosmo/fulfillment/FulfillmentAction;`
(verified by per-file `^\.super` grep — **24** total):

```
FulfillmentAction$ActiveDataPlan
FulfillmentAction$AddTask
FulfillmentAction$AddTimer
FulfillmentAction$AddToList
FulfillmentAction$AddToMemory
FulfillmentAction$BrowsingAgent
FulfillmentAction$CallAgent
FulfillmentAction$CreateCalendarEvent
FulfillmentAction$CreateDoc
FulfillmentAction$DeepResearch
FulfillmentAction$DefineTerm
FulfillmentAction$DraftEmail
FulfillmentAction$DraftSms
FulfillmentAction$InvokeAgentQ
FulfillmentAction$Navigation
FulfillmentAction$PlayMusic
FulfillmentAction$ProvideEventContext
FulfillmentAction$ProvideInsight
FulfillmentAction$ProvidePersonContext
FulfillmentAction$ProvideRecall
FulfillmentAction$QueryPhotos
FulfillmentAction$RemovedAction
FulfillmentAction$Unsupported
FulfillmentAction$WebSearch
```

Other sibling smali files in the same package — **not** sealed-class
subtypes (each `.super` verified):

```
FulfillmentAction$Status                              (.super Ljava/lang/Enum;)
FulfillmentAction$ActiveDataPlan$PlanExecutionState   (.super Ljava/lang/Enum;) — nested in ActiveDataPlan
FulfillmentAction$Companion                           (.super Ljava/lang/Object;)
FulfillmentAction$Companion$deserializeList$listType$1
                                                      (.super Lcom/google/gson/reflect/TypeToken;)
FulfillmentAction$WhenMappings                        (.super Ljava/lang/Object;) — Kotlin-generated when-table
FulfillmentActionGsonAdapterFactory                   (separate Gson adapter, not nested in FulfillmentAction)
```

The 24 sealed-class subtypes overlap the 23 `ACTION_TYPE_*` enum values
in the schema (see §C.6) plus two internal-use sentinels
(`RemovedAction`, `Unsupported`); one `ACTION_TYPE_*` enum value
(`ACTION_TYPE_UNKNOWN`) is the unknown-action discriminator and has no
dedicated subtype, while `ACTION_TYPE_ACTIVE_DATA_PLAN` maps to
`ActiveDataPlan` (with its nested `PlanExecutionState` enum).

### F.2 `com.google.research.air.cosmo.fulfillment.FulfillmentActionHandlerImpl`

Constructor:

```
public <init>(
    android.content.Context,
    ActionsRepository,
    com.google.research.air.cosmo.memory.MemoryStorage,
    AgentQSessionManager,
    java.util.concurrent.Executor,
    java.util.concurrent.Executor
)
```

Methods (`execute*` per action type):

```
public execute(FulfillmentAction): void
private final executeAddTask(FulfillmentAction$AddTask): void
private final executeAddTimer(FulfillmentAction$AddTimer): void
private final executeAddToList(FulfillmentAction$AddToList): void
private final executeAddToMemory(FulfillmentAction$AddToMemory): void
private final executeCallAgent(FulfillmentAction$CallAgent): void
private final executeCreateCalendarEvent(FulfillmentAction$CreateCalendarEvent): void
private final executeDeepResearch(FulfillmentAction$DeepResearch): void
private final executeDraftSms(FulfillmentAction$DraftSms): void
private final executeInvokeAgentQ(FulfillmentAction$InvokeAgentQ): void
private final executeNavigation(FulfillmentAction$Navigation): void
private final executePlayMusic(FulfillmentAction$PlayMusic): void
private final executeProvideInsight(FulfillmentAction$ProvideInsight): void
private final executeQueryPhotos(FulfillmentAction$QueryPhotos): void
private final executeWebSearch(FulfillmentAction$WebSearch): void
private final launchIntent(android.content.Intent, java.lang.String): void
```

### F.3 `com.google.research.air.cosmo.fulfillment.ActionsRepository`

Constructor:

```
public <init>(java.util.concurrent.Executor)
```

Methods:

```
public final addFulfillmentHistory(FulfillmentDebugInfo): void
public final addTestActions(): void
public final clearAllActions(): void
public final clearAllDebugInfos(): void
public final getFulfillmentDebugInfo(java.lang.String): FulfillmentDebugInfo
public final getFulfillmentHistory(): kotlinx.coroutines.flow.StateFlow
public final getHasNewActions(): kotlinx.coroutines.flow.SharedFlow
public final getUserFacingActions(): kotlinx.coroutines.flow.StateFlow
public getCurrentActions(): kotlinx.coroutines.flow.StateFlow
public getCosmoRemovedActions(): kotlinx.coroutines.flow.StateFlow
public getExecutedActions(): kotlinx.coroutines.flow.StateFlow
public getUserDismissedActions(): kotlinx.coroutines.flow.StateFlow
public final listFulfillmentHistory(java.lang.String): java.util.List
public markActionAsDismissed(java.lang.String, Continuation): Object
public final markActionAsExecuted(java.lang.String): void
public maybeUpdateActionsList(java.util.List, java.lang.String, Continuation): Object
private final retireAction(java.lang.String, FulfillmentAction$Status): void
```

### F.4 `com.google.research.air.cosmo.fulfillment.AgentQSessionManagerImpl`

Constructor:

```
public <init>(
    android.content.Context,
    com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer,
    java.util.concurrent.Executor
)
```

Methods:

```
public sendPrompt(java.lang.String, Continuation): Object
public close(): void
private final ensureConnection(Continuation): Object
private final ensureListening(
    com.google.research.air.libraries.common.android.a2a.AgentClient,
    com.google.research.air.libraries.common.android.a2a.AgentTaskId
): void
private final resetConnection(): void
private final trySendPrompt(java.lang.String, int, Continuation): Object
```

The `a2a.AgentClient` / `a2a.AgentTaskId` types are external (provided by
`com.google.research.air.libraries.common.android.a2a` — outside the
Cosmo package tree).

[P:no-special] for the Java surface; the `AgentClient` likely binds a
remote service whose details are not in this APK.

---

## Section G — `CosmoController` (the seam between captors and fulfillment)

`CosmoController` was enumerated in `text_stack_map.md` §F. The
LLM-side connections established in this analysis:

- The `IntentExtractor` parameter to `CosmoController` (covered in
  `text_stack_map.md` §E) takes an `LLM` instance (per
  `IntentExtractorImpl.<init>` signature).
- The `ActionsRepository` parameter is the same instance used by
  `FulfillmentOrchestratorImpl` and `FulfillmentRunner`.
- The gatekeeper `SharedFlow<GatekeepingResult>` parameter is consumed by
  both `IntentExtractorImpl` and `FulfillmentOrchestratorImpl.startGatekeeperListener`.

### G.1 `DoubleTapActivity`

`com.google.research.air.cosmo.fulfillment.DoubleTapActivity` (verified
path; the file lives at
`classes38/com/google/research/air/cosmo/fulfillment/DoubleTapActivity.smali`,
not under `…/fulfillment/actions/`).

Class hierarchy (verified by grep):

```
DoubleTapActivity.smali
    .super Lcom/google/research/air/cosmo/fulfillment/Hilt_DoubleTapActivity;

Hilt_DoubleTapActivity.smali
    .super Landroidx/activity/ComponentActivity;
```

Public methods (`^\.method [^s]*public` grep):

```
public <init>()
```

(No further public methods declared on `DoubleTapActivity` itself; any
behavior is in inherited `ComponentActivity` callbacks. Its presence
matches the `FulfillmentTriggerType.PIXEL_PHONE_DOUBLE_TAP` enum value
in §E.3.)

---

## Section H — Routing decision points (artifact-only)

Cosmo's on-device LLM-routing decisions are visible at three points:

### H.1 `NetworkAwareLLM.getActiveLlm()`

(Body not recovered; constructor takes two `LLM` delegates and a
`Context`.)

### H.2 `LLMStatsRepository.isPrivateInferenceEnabled(): StateFlow<Boolean>`

A boolean `StateFlow` exists named `isPrivateInferenceEnabled`. Mutated
via `updatePrivateInferenceEnabled(boolean)`.
`PrivateInferenceArateaLLM` calls `updatePrivateInferenceEnabled` itself
on success or failure paths.

### H.3 `FulfillmentRunner.<init>` takes two `LLM` parameters

The `FulfillmentRunner` constructor's two `LLM` slots (declared verbatim
in §E.4) are the only two `LLM` slots on the runner. The body of
`fulfill-yxL6bBk` was not extracted, so which call site inside the
runner uses which delegate is not enumerated here.

### H.4 The `setting.CosmoSetting` toggles observed in capture-side smali

const-strings invoked across the captor classes (from the audio doc and
this one) reference `CosmoSetting` getters that influence routing:

```
isUseNanoappAudioEnabled    (from SODAAmbientAudioCaptor — selects AOC vs mic)
isRawAudioGatekeeperEnabled (from SODAAmbientAudioCaptor — gate audio gatekeeper)
isRealtimeProcessingEnabled (from SODAAmbientAudioCaptor)
getBudsAudioEnabled         (from SODABudsAudioCaptor)
getGlobalCosmoEnabled       (from SODABudsAudioCaptor)
isAmbientAudioEnabled       (from CosmoGlassManager)
isConnectToGlassEnabled     (from CosmoGlassManager)
isGlassesCameraEnabled      (from CosmoGlassManager)
getGlassesCameraEnabled     (CosmoSetting)
```

These are the enumerable settings whose getters appear in the captor
`invoke-…` lines; the full `CosmoSetting` proto's field list is not
enumerated in this analysis.

---

## Section I — Reachability summary

| Path | Reach | Reason from artifact |
|---|---|---|
| `AICoreLLM.inference` | [R:OK] on Pixel with AICore + Nano installed | declares `BIND_SERVICE` on `com.google.android.apps.aicore.service`; calls `AiCoreClient.listFeatures` and looks for `AiFeature 108 (LLM-Nano V3)` |
| `AICoreLLM.inference` | [R:BLOCKED] on devices without AICore | `AiFeature 108 (LLM-Nano V3) not found.` log path; `AICore inference failed.` |
| `PrivateInferenceArateaLLM.inference` | [R:OK-NETWORK] | `INTERNET` declared; uses `com.google.android.libraries.mdi.privatearatea` client; remote service is opaque to this APK but invocations exist |
| `PrivateInferenceArateaBatchLLM.runBatchInference` | [R:OK-NETWORK] | same client factory chain |
| `LTCLLM.inference` | (body not recovered) | wrapper around an LLM impl whose target is not enumerated here |
| `LocalLLM.inference` | (body not recovered) | only inherited members visible |
| `HybridLLM.inference` | (body not recovered) | only inherited members visible |
| `ServerLLM.inference` | (body not recovered) | only inherited members visible |
| `NetworkAwareLLM.inference` | [R:OK] | wraps two `LLM` delegates, selects one in `getActiveLlm()` (body not recovered) |
| `IntentExtractorImpl` | [R:OK on the LLM dependency] | takes an `LLM` argument; output flow is consumed by `CosmoController` |
| `FulfillmentOrchestratorImpl.handleUserQuery` | [R:OK on the LLM dependency] | declared on the public interface |
| `FulfillmentRunner.fulfill-yxL6bBk` | [R:OK on the two LLM dependencies] | invoked from `FulfillmentOrchestratorImpl.doFulfillment` (per name overlap) |
| `FulfillmentActionHandlerImpl.execute*` per action | [R:OK on Intent permissions] | each `execute*` builds an `Intent` (e.g. for calendar / SMS / dialer / web search); the relevant runtime permissions are user-grantable |
| `AgentQSessionManagerImpl.sendPrompt` | (external; not in APK) | binds to a `com.google.research.air.libraries.common.android.a2a.AgentClient`; that library's binder target is not in Cosmo |
| Skill authoring (`SkillGenerator` / `GatekeepingGenerator`) | [R:OK on the LLM dependency] | takes an `LLM` argument; produces protos consumed by the gatekeeper |
| Memory retrieval (`FulfillmentRunner.retrieveMemories`) | [R:OK on the LLM dependency] — the runner's inner class `FulfillmentRunner$retrieveMemories$searchResults$1$1$1$1` invokes `MemoryStorage$-CC.search$default(...)`. In this build the bound `MemoryStorage` is `NoOpMemoryImpl` (see `text_stack_map.md` §D.4–D.5), so the search call returns empty. |  |
| Memory-retrieval prompt assembly (`PromptBuilder.buildMemoryRetrievalPrompt`) | [R:OK] — the method just renders `memory_retrieval_prompt.jinja2` from a query string and a max-queries int; it does not itself invoke `MemoryStorage`. |  |

---

## Section J — Provenance

- `cosmo_baksmali/classes*/com/google/research/air/cosmo/` directories:
  `lib/llm`, `fulfillment`, `fulfillment/prompts`, `fulfillment/actions`,
  `skills`, `skills/generation`, `controller`,
  `agents/proactivesuggestions`.
- `assets/` files extracted via `unzip -p`:
  `intent_extraction_prompt.jinja2`, `gatekeeper_trigger_prompt.jinja2`,
  `skill_definition_prompt.jinja2`, `fulfillment_prompt.jinja2`,
  `reactive_fulfillment_prompt.jinja2`, `memory_retrieval_prompt.jinja2`,
  `gboard_prompt.jinja2`, `filtered_skill_schema.textproto`,
  `gatekeeper_trigger_info_schema.textproto`.
- `cosmo_baksmali_imports/native_libraries.md` for ELF NEEDED listings of
  `libpi_client_session_config_jni.so` and
  `liboak_session_config_builder_jni.so`.
- `aapt2 dump permissions` output in `audio_stack_map.md`.
- `audio_stack_map.md`, `text_stack_map.md`, `image_stack_map.md` for
  cross-references.

This document is static analysis only.
