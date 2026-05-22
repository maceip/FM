# Cosmo FM — Executive Summary (Android XR / Gemini Glasses)

**Glanceable ambient intelligence for the workplace**

---

## One sentence

Cosmo FM helps people complete everyday work by voice—timers, calendar, search, drafts—surfaced as **one confirmable prompt at a time**, designed for **Gemini glasses** when hands and attention are elsewhere.

---

## The problem

Workers constantly stop to unlock a phone, find an app, and type. Voice tools often feel like chat apps or activate on background noise—poor fit for offices, clinics, and field work.

---

## Our approach

| Step | What happens |
|------|----------------|
| **Listen** | Device captures speech locally (phone, earbuds, watch; glasses next) |
| **Qualify** | On-device models decide: *Was this meant for the assistant?* |
| **Act** | Gemini / on-device AI produces a **structured action**, not a essay |
| **Confirm** | User sees one card—Start timer? Add to calendar?—and taps yes or dismisses |

**Privacy-first:** Qualification runs on-device before generative AI. Users control which suggestions are allowed (timers, calendar, search, etc.).

---

## Why Gemini glasses

- **Hands busy** — cooking, walking, equipment, patient care  
- **Eyes forward** — no phone pull-out in meetings or on a factory floor  
- **Seconds, not minutes** — one line of status, one card, one decision  

Phone = compute + settings. Glasses = what matters *right now*.

---

## Example moments

- “Set a timer for ten minutes” → *10-minute timer — Start?*  
- “Schedule with Avery Tuesday at three” → *Calendar event — Confirm?*  
- “Draft a reply about the renewal” → *Email draft — Review?*  

---

## Status today

End-to-end prototype **running on Pixel**: voice → qualification → fulfillment → action card. Automated device tests pass for timer and audio-injection paths. **XR display projection** is the next integration step.

---

## Ask

Early Android XR access to project our confirmation UI to Gemini glasses and validate workplace scenarios with real wearers.

---

**Full design brief:** `docs/android-xr-cosmo-fm-design-brief.md`
