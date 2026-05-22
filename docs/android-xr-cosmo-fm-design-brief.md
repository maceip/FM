# Cosmo FM — Design Brief for Android XR & Gemini Glasses

**Document type:** Experience & system design (supporting materials)  
**Version:** 1.0 — May 2026  
**Contact context:** Android XR developer preview / Gemini glasses program application

---

## 1. Executive summary

**Cosmo FM** is an ambient workplace assistant that helps people act on spoken intent without opening apps or reading long chat transcripts. It is designed for **glanceable, confirmable moments**: a timer before a meeting, a calendar hold, a draft reply, a quick search—surfaced when the user clearly asks, dismissed when they do not.

We are applying to Android XR to make **Gemini glasses the primary display** for those moments: hands busy, eyes forward, phone in pocket. The phone remains the secure compute and settings surface; the glasses show **one timely prompt at a time**.

---

## 2. Problem statement

Knowledge and frontline workers lose minutes hundreds of times per day to **context switching**: unlock phone → find app → type → confirm. Voice assistants often make this worse by treating every utterance as a conversation, or by activating on background speech.

Enterprises need assistance that is:

- **Intentional** — responds when the user means it, not on ambient chatter  
- **Private** — sensitive decisions stay on-device where possible  
- **Glanceable** — readable in seconds, not scrollable threads  
- **Confirmable** — high-impact actions require an explicit yes  

---

## 3. Product vision on Gemini glasses

### 3.1 The experience we want to build

| State | What the wearer sees | What happens |
|--------|----------------------|--------------|
| **Idle** | Minimal status (optional)—assistant available, not intrusive | Listening locally; no cloud transcript stream for routine audio |
| **Working** | Short status line—e.g. “Setting a timer…” | On-device models classify intent; fulfillment runs locally when possible |
| **Ready** | Single action card with clear primary action | e.g. “10-minute timer — Start?” / “Add to calendar — Confirm?” |
| **Dismissed** | Returns to idle | User declined or timeout; no persistent chat history on-glass |

The design language is **calm and operational**—closer to a heads-up display for work than a social feed or chat UI.

### 3.2 Primary use cases (XR-first)

1. **Hands-busy productivity** — Cooking, commuting, warehouse floor, clinical rounds: speak intent; confirm on-glass; never fish for a phone.  
2. **Meeting side-channel** — Capture “follow up with Avery Tuesday” or “remind me after this call” without breaking eye contact.  
3. **Field & client-facing roles** — Navigation, timer, quick lookup, draft message—one card, one decision.  
4. **Enterprise policy alignment** — Per-skill toggles (timers, calendar, search, drafts) so IT can scope what the assistant may suggest.

---

## 4. System design (adapted overview)

Cosmo FM follows a **listen → qualify → act → confirm** pipeline. The diagram below is the logical flow we implement today on Pixel and intend to project to XR.

```
┌─────────────────────────────────────────────────────────────────┐
│  CAPTURE (phone, watch, earbuds, future: glasses mic/camera)     │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  QUALIFY — “Did the user mean the assistant?”                    │
│  On-device speech + intent detection; privacy-first gating       │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  FULFILL — Gemini / on-device LLM + structured action schema     │
│  Timers, calendar, lists, search, drafts, navigation, …          │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  SURFACE — Glanceable confirmation (phone today → glasses target) │
└─────────────────────────────────────────────────────────────────┘
```

### 4.1 Capture layer

Multiple context sources feed short-lived on-device buffers (not a permanent cloud recording):

| Source | Purpose |
|--------|---------|
| Ambient microphone | Primary voice input |
| Earbuds & watch | Trusted audio when user is clearly engaged |
| Screen & notifications (optional) | Context for smarter suggestions |
| **XR / glasses (target)** | Projected display + future capture from wearables |

### 4.2 Qualification layer (privacy-first)

Before any large language model runs, Cosmo FM applies **on-device qualification**:

- Detect whether speech is **directed at the assistant** vs. background conversation  
- Map utterances to **allowed skill categories** (timer, calendar, search, etc.)  
- Respect **user and org settings** (which suggestions are enabled)

This reduces false activations and limits what reaches generative models—a requirement for enterprise and regulated environments.

### 4.3 Fulfillment layer

Qualified intents enter a **structured fulfillment pipeline**:

1. Select enabled skills and context (recent audio, screen snippets per policy)  
2. Call on-device AI (Gemini Nano / local model where available) with a **fixed action schema**—not open-ended chat  
3. Parse a single structured response (e.g. timer duration, event time, draft text)  
4. Apply **confirmation policy** before side effects (timers, calendar writes, messages)

Supported action families include: timers, calendar events, lists, web search, document drafts, email/SMS drafts, navigation, music, and research-style tasks—with explicit downgrade paths when a first-party API is unavailable.

### 4.4 Surface layer (XR target)

Today, confirmations appear on the phone as **action cards** tied to a lightweight ambient UI. For Android XR, we project the same **Ready** state to the glasses viewport:

- One primary action per card  
- Readable typography and contrast for optical see-through  
- Minimal animation; fast dismiss  

Phone and glasses stay in sync: initiate on either; confirm where the user is looking.

---

## 5. Privacy, security & enterprise controls

| Principle | Implementation direction |
|-----------|---------------------------|
| **Data minimization** | Qualification on-device before LLM; no “send all audio to cloud” path for gating |
| **User control** | Global on/off, snooze, active hours, per-skill suggest toggles |
| **Encrypted settings** | Preferences stored in encrypted on-device storage |
| **Auditability** | Fulfillment runs logged locally for debug/support (telemetry schema in place) |
| **Confirmation for side effects** | Timers, calendar, messages require explicit user approval |

---

## 6. Gemini & on-device AI strategy

Cosmo FM is built **on-device first**, with optional remote fallback for environments that allow it:

| Tier | Role |
|------|------|
| **Gemini Nano (AICore)** | Preferred on supported Pixel / Android devices when available |
| **On-device Gemma (LiteRT-LM)** | Fallback bundle for devices without AICore feature access |
| **Simulation / test mode** | Deterministic responses for development and golden-path validation |
| **Remote inference (future)** | Enterprise-hosted option with certificate pinning—not required for XR preview |

The assistant is not “Gemini in a chat window.” Gemini (or equivalent local model) powers **structured fulfillment** after qualification—producing JSON-like actions the app can render and execute.

---

## 7. Current prototype status (Pixel)

We have a **working end-to-end prototype** on Pixel hardware, validated with automated golden tests:

| Capability | Status |
|------------|--------|
| Voice → intent detection → structured fulfillment → action card | ✅ Proven (device tests) |
| Timer use case end-to-end (“set a timer for 10 minutes”) | ✅ Proven |
| Audio fixture injection (WAV → speech → skill → card) | ✅ Proven |
| On-device LLM routing (local / hybrid) | ✅ Proven on Pixel 10 class devices |
| Glanceable ambient UI (idle / working / ready states) | ✅ Implemented on phone |
| **Projection to Gemini glasses** | 🔵 Target for XR program |

Known gaps (honest scope): advanced audio-model accuracy on all phrases, full glasses capture loop, and enterprise remote-inference hosting.

---

## 8. Android XR integration plan

### Phase A — Display parity (preview)

- Bind existing **Ready** action cards to Android XR projected surface API  
- Sync dismissal and confirmation across phone ↔ glasses  
- Tune layout for field of view and readability  

### Phase B — Wearable capture

- Route glasses microphone (when available) into the same qualification pipeline  
- Treat glasses as a trusted capture source alongside phone and earbuds  

### Phase C — Enterprise pilots

- Configurable skill packs per vertical (healthcare, logistics, professional services)  
- Optional managed policy hooks for suggest/record flags  

---

## 9. What we are asking from the program

1. **Early access** to Android XR projected UI and Gemini glasses display path  
2. **Guidance** on recommended interaction patterns for confirmable cards in see-through optics  
3. **Partnership** to validate hands-busy workplace scenarios with real wearers  

We bring a **running Pixel prototype**, a clear privacy posture, and a product shape that fits glasses better than a phone chat app.

---

## 10. Appendix — Technical reference (internal architecture)

For reviewers who want engineering depth, the open design corpus includes:

| Document | Contents |
|----------|----------|
| `docs/architecture.md` | Full system architecture (capture, gatekeeping, fulfillment, LLM) |
| `docs/architecture-delta.md` | Contract rules and parity notes |
| `docs/fm-ui-pixel-targets.md` | Visual targets for ambient confirmation UI |
| `docs/blocker-register.md` | Active risks and exit criteria |
| `README.md` | Capability matrix and build/run instructions |

Repository: **FM (Cosmo FM)** — ambient agent with local on-device intelligence.

---

*Cosmo FM is an independent reimplementation of ambient-assistant patterns for research and product exploration. This brief describes our intended experience on Android XR; it is not a Google product announcement.*
