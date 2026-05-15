# Phase 5 Reachability Matrix

This matrix records what is reachable from the Genesis debug-keystore APK on a retail Pixel and where each substitution point lives.

## Runtime Surfaces

| Surface | Reachable | Code Path | Notes |
|---|---:|---|---|
| Microphone capture | Yes | `genesis/app/src/main/kotlin/.../lib/record/RecordService.kt` | Requires `RECORD_AUDIO`; foreground service type is `microphone`. |
| Device camera capture | Yes | `voiceassistant/camera/CameraCapture.kt` | Requires `CAMERA`; gated by `cameraEnabled`. |
| Notification capture | Yes | `notificationcapture/NotificationCapture.kt` | Requires Android notification listener settings plus runtime notifications on API 33+. |
| Accessibility screen context | Yes | `accessibility/Accessibility.kt`, `contentcapture/ScreenCapture.kt` | Requires user-enabled accessibility service. |
| Screen interaction capture | Yes | `interactioncapture/InteractionCapture.kt` | Enforced by `screenInteractionCaptureEnabled`. |
| Location context | Yes | `locationcapture/LocationCapture.kt` | Requires coarse or fine location and `locationCaptureEnabled`. |
| AICore local LLM | Conditional | `lib/llm/AICoreClientWrapper.kt`, `lib/llm/LLM.kt` | Reachable when AICore is installed and exposes the public generation API. |
| Remote inference | Conditional | `lib/llm/RemoteInferenceClient.kt` | Reachable when `cosmo.remoteInference.baseUrl`, certificate pin, and issuer contract are configured. |
| XR projected activity | Conditional | `glasses/CosmoGlass.kt` | Requires projected display category and available XR host. |
| Wear listener | Conditional | `lib/wearable/Wearable.kt` | Requires Google Play Services wearable routing. |
| Device audio output capture | No | Not implemented | Android signature permissions block retail reachability. |
| AOC nanoapp audio | No | Not implemented | Requires firmware and context hub privileges. |

## Substitution Points

| Point | Interface | Implementation Slots |
|---|---|---|
| Local/server LLM | `LLM` | `@LocalLLM`, `@ServerLLM`, `@HybridLLM`, `@SimulatedLLM` in `lib/llm/LLM.kt` |
| Network routing | `NetworkAwareLLM`, `NetworkPolicyProvider` | Local vs remote route state in `lib/llm/LLM.kt` |
| Gatekeeper fusion | `GatekeepingModel`, `GatekeeperOutputSource` | Regex and model-backed outputs in `gatekeeping/Gatekeeping.kt` |
| Action dispatch | `FulfillmentActionHandler` | Per-subtype dispatch in `fulfillment/Fulfillment.kt` |
| Context persistence | `ContextStore` | SQLCipher Room implementation in `context/ContextStore.kt` |

## Fulfillment Actions

| Action | Status | Code Path |
|---|---|---|
| Add task | Implemented | `FulfillmentAction.AddTask`, text draft launch |
| Add timer | Implemented | `AlarmClock.ACTION_SET_TIMER` |
| Add to list | Implemented | text draft launch |
| Add to memory | Implemented | `MemoryStorage.add` |
| Browsing agent | Implemented | `TaskMonitorActivity` |
| Call agent | Implemented with permission gate | `CALL_PHONE` check before dial/call intent |
| Create calendar event | Implemented with permission gate | `CalendarContract.Events` insert |
| Create doc | Implemented | text draft launch |
| Deep research | Implemented | web/search intent path |
| Define term | Implemented | web/search intent path |
| Draft email | Implemented | email intent path |
| Draft SMS | Implemented | SMS intent path |
| Invoke AgentQ | Implemented when external surface exists | external session intent/bind path |
| Navigation | Implemented | maps/navigation intent path |
| Play music | Implemented | media search intent path |
| Provide event context | Implemented | answer action |
| Provide insight | Implemented | answer action |
| Provide person context | Implemented | answer action |
| Provide recall | Implemented | answer action |
| Query photos | Implemented | photos/search intent path |
| Web search | Implemented | `SearchManager` / browser intent |
| Active data plan | Implemented as answer/action record | sealed subtype retained |
| Removed action | Implemented | repository state transition |
| Unsupported | Implemented | unknown JSON action types map here and are not executed |

## Release Reachability Gate

1. Build a signed minified APK from `genesis` with `./gradlew :app:assembleRelease`.
2. Install on a retail Pixel with AICore available.
3. Grant runtime permissions through the in-app Settings/Capture surfaces.
4. Run microphone, camera, notification, accessibility, local LLM, remote LLM, and at least one side-effect action.
5. Confirm the four substitution points still compile and can be swapped through Hilt bindings without changing call sites.
