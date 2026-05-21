/**
 * Explainer — page 2. The AgentV design document: the calm/operational
 * design language, the listen → qualify → act → confirm pipeline as boxes
 * and arrows, and the supporting tables, over the shared dune backdrop.
 */
import { Fragment, useRef, type ReactNode } from "react";
import { Canvas, useFrame } from "@react-three/fiber";
import * as THREE from "three";
import { OrbMesh } from "../three/OrbMesh";
import { ORB_PRESETS } from "../three/orbPresets";

const PANEL =
  "rounded-2xl border border-white/[0.08] bg-[#0b1118]/55 backdrop-blur-xl";

/* ---------- shared primitives --------------------------------------- */

function Section({
  n,
  title,
  children,
}: {
  n: string;
  title: string;
  children: ReactNode;
}) {
  return (
    <section className="mt-20">
      <div className="flex items-baseline gap-4 border-b border-white/[0.08] pb-4">
        <span className="font-mono text-[13px] tracking-[0.1em] text-[var(--color-accent)]">
          {n}
        </span>
        <h3 className="text-[26px] font-semibold tracking-[-0.01em] text-[var(--color-text-primary)]">
          {title}
        </h3>
      </div>
      <div className="mt-7">{children}</div>
    </section>
  );
}

function SubHead({ children }: { children: ReactNode }) {
  return (
    <h4 className="mb-4 mt-10 font-mono text-[12px] uppercase tracking-[0.2em] text-[var(--color-text-secondary)] first:mt-0">
      {children}
    </h4>
  );
}

function Lead({ children }: { children: ReactNode }) {
  return (
    <p className="text-[14px] leading-relaxed text-[var(--color-text-secondary)]">
      {children}
    </p>
  );
}

function DataTable({
  headers,
  rows,
}: {
  headers: string[];
  rows: ReactNode[][];
}) {
  return (
    <div className={`${PANEL} overflow-hidden`}>
      <table className="w-full border-collapse text-left">
        <thead>
          <tr className="border-b border-white/[0.1] bg-white/[0.02]">
            {headers.map((h) => (
              <th
                key={h}
                className="px-5 py-3 font-mono text-[10px] uppercase tracking-[0.16em] text-[var(--color-text-faint)]"
              >
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr
              key={i}
              className="border-b border-white/[0.05] last:border-0"
            >
              {row.map((cell, j) => (
                <td
                  key={j}
                  className={`px-5 py-3.5 align-top text-[13px] leading-relaxed ${
                    j === 0
                      ? "whitespace-nowrap font-medium text-[var(--color-text-primary)]"
                      : "text-[var(--color-text-secondary)]"
                  }`}
                >
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Numbered cards, e.g. the primary use cases. */
function NumberedGrid({ items }: { items: [string, string][] }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {items.map(([title, body], i) => (
        <div key={title} className={`${PANEL} p-5`}>
          <div className="font-mono text-[12px] text-[var(--color-accent)]">
            {String(i + 1).padStart(2, "0")}
          </div>
          <div className="mt-2 text-[14px] font-semibold text-[var(--color-text-primary)]">
            {title}
          </div>
          <div className="mt-1.5 text-[13px] leading-relaxed text-[var(--color-text-secondary)]">
            {body}
          </div>
        </div>
      ))}
    </div>
  );
}

function Bullets({ items }: { items: ReactNode[] }) {
  return (
    <ul className="space-y-2.5">
      {items.map((it, i) => (
        <li
          key={i}
          className="flex gap-3 text-[13px] leading-relaxed text-[var(--color-text-secondary)]"
        >
          <span className="mt-[7px] h-1 w-1 shrink-0 rounded-full bg-[var(--color-accent)]" />
          <span>{it}</span>
        </li>
      ))}
    </ul>
  );
}

/* ---------- pipeline: boxes + arrows --------------------------------- */

const PIPELINE: [string, string][] = [
  [
    "Capture",
    "Phone, watch, earbuds — and, as a target surface, glasses mic and camera.",
  ],
  [
    "Qualify",
    "“Did the user mean the assistant?” On-device speech + intent detection, with privacy-first gating before anything reaches a generative model.",
  ],
  [
    "Fulfill",
    "Gemini / on-device LLM against a fixed structured action schema — timers, calendar, lists, search, drafts, navigation.",
  ],
  [
    "Surface",
    "A single glanceable confirmation — on the phone today, the glasses viewport as the target.",
  ],
];

function Arrow() {
  return (
    <div className="flex h-10 flex-col items-center">
      <div className="w-px flex-1 bg-gradient-to-b from-[var(--color-accent)]/70 to-[var(--color-accent)]/15" />
      <div className="h-0 w-0 border-x-[5px] border-t-[7px] border-x-transparent border-t-[var(--color-accent)]" />
    </div>
  );
}

function Pipeline() {
  return (
    <div className="mx-auto flex max-w-[560px] flex-col items-stretch">
      {PIPELINE.map(([k, d], i) => (
        <Fragment key={k}>
          <div className={`${PANEL} px-6 py-5`}>
            <div className="flex items-center gap-3">
              <span className="font-mono text-[11px] text-[var(--color-text-faint)]">
                {String(i + 1).padStart(2, "0")}
              </span>
              <span className="font-mono text-[13px] uppercase tracking-[0.22em] text-[var(--color-accent)]">
                {k}
              </span>
            </div>
            <div className="mt-2 text-[13px] leading-relaxed text-[var(--color-text-secondary)]">
              {d}
            </div>
          </div>
          {i < PIPELINE.length - 1 && <Arrow />}
        </Fragment>
      ))}
    </div>
  );
}

/* ---------- status pills -------------------------------------------- */

function Status({ done, label }: { done: boolean; label: string }) {
  return (
    <span className="inline-flex items-center gap-2 whitespace-nowrap">
      <span
        className="h-1.5 w-1.5 rounded-full"
        style={{ background: done ? "var(--color-good)" : "var(--color-accent)" }}
      />
      <span className={done ? "text-[var(--color-good)]" : "text-[var(--color-accent)]"}>
        {label}
      </span>
    </span>
  );
}

/* ---------- the design-language orb --------------------------------- */

function SpinningOrb() {
  const ref = useRef<THREE.Group>(null!);
  useFrame((s) => {
    ref.current.rotation.y = s.clock.elapsedTime * 0.4;
  });
  return <OrbMesh ref={ref} state={ORB_PRESETS[1]} />;
}

function OrbBadge() {
  return (
    <Canvas
      camera={{ position: [0, 0, 3.6], fov: 42 }}
      gl={{ antialias: true, alpha: true }}
      dpr={[1, 2]}
    >
      <ambientLight intensity={1.2} />
      <directionalLight position={[3, 4, 4]} intensity={1.9} />
      <directionalLight position={[-3, -2, 2]} intensity={0.5} color="#4d8dff" />
      <SpinningOrb />
    </Canvas>
  );
}

/* ---------- page ----------------------------------------------------- */

export function DesignDoc() {
  return (
    <section className="relative w-full">
      {/* darken the dune backdrop so the document stays legible */}
      <div className="pointer-events-none absolute inset-0 bg-[#04060c]/72" />

      <div className="relative mx-auto max-w-[940px] px-8 py-28 sm:px-12">
        {/* header */}
        <div className="flex items-start justify-between gap-8">
          <div className="max-w-[560px]">
            <div className="font-mono text-[12px] uppercase tracking-[0.34em] text-[var(--color-accent)]">
              Design Document
            </div>
            <h2 className="mt-5 text-[44px] font-semibold leading-[1.05] tracking-[-0.02em] text-[var(--color-text-primary)]">
              AgentV on Gemini glasses
            </h2>
            <p className="mt-5 text-[14px] leading-relaxed text-[var(--color-text-secondary)]">
              A calm, operational ambient assistant — closer to a heads-up
              display for work than a social feed or a chat UI. This brief
              describes the experience, the on-device pipeline, and the path
              to Android XR.
            </p>
          </div>
          <div className="hidden h-[180px] w-[180px] shrink-0 sm:block">
            <OrbBadge />
          </div>
        </div>

        {/* 01 — product vision */}
        <Section n="01" title="Product vision on Gemini glasses">
          <SubHead>1.1 — The experience we want to build</SubHead>
          <DataTable
            headers={["State", "What the wearer sees", "What happens"]}
            rows={[
              [
                "Idle",
                "Minimal status (optional) — available, not intrusive",
                "Listening locally; no cloud transcript stream for routine audio",
              ],
              [
                "Working",
                "A short status line — e.g. “Setting a timer…”",
                "On-device models classify intent; fulfillment runs locally when possible",
              ],
              [
                "Ready",
                "A single action card with one clear primary action",
                "e.g. “10-minute timer — Start?” / “Add to calendar — Confirm?”",
              ],
              [
                "Dismissed",
                "Returns to idle",
                "User declined or timed out; no persistent chat history on-glass",
              ],
            ]}
          />
          <p className="mt-5 text-[13px] leading-relaxed text-[var(--color-text-faint)]">
            The design language is{" "}
            <span className="text-[var(--color-text-secondary)]">
              calm and operational
            </span>{" "}
            — a heads-up display for work, not a feed.
          </p>

          <SubHead>1.2 — Primary use cases (XR-first)</SubHead>
          <NumberedGrid
            items={[
              [
                "Hands-busy productivity",
                "Cooking, commuting, warehouse floor, clinical rounds: speak intent, confirm on-glass, never fish for a phone.",
              ],
              [
                "Meeting side-channel",
                "Capture “follow up with Avery Tuesday” or “remind me after this call” without breaking eye contact.",
              ],
              [
                "Field & client-facing roles",
                "Navigation, timer, quick lookup, draft message — one card, one decision.",
              ],
              [
                "Enterprise policy alignment",
                "Per-skill toggles (timers, calendar, search, drafts) so IT can scope what the assistant may suggest.",
              ],
            ]}
          />
        </Section>

        {/* 02 — system design */}
        <Section n="02" title="System design">
          <Lead>
            AgentV follows a{" "}
            <span className="text-[var(--color-text-primary)]">
              listen → qualify → act → confirm
            </span>{" "}
            pipeline — the logical flow implemented today on Pixel and intended
            to project to XR.
          </Lead>
          <div className="mt-8">
            <Pipeline />
          </div>

          <SubHead>2.1 — Capture layer</SubHead>
          <Lead>
            Multiple context sources feed short-lived on-device buffers — not a
            permanent cloud recording.
          </Lead>
          <div className="mt-4">
            <DataTable
              headers={["Source", "Purpose"]}
              rows={[
                ["Ambient microphone", "Primary voice input"],
                [
                  "Earbuds & watch",
                  "Trusted audio when the user is clearly engaged",
                ],
                [
                  "Screen & notifications (optional)",
                  "Context for smarter suggestions",
                ],
                [
                  "XR / glasses (target)",
                  "Projected display + future capture from wearables",
                ],
              ]}
            />
          </div>

          <SubHead>2.2 — Qualification layer (privacy-first)</SubHead>
          <Lead>
            Before any large language model runs, AgentV applies on-device
            qualification:
          </Lead>
          <div className="mt-4">
            <Bullets
              items={[
                "Detect whether speech is directed at the assistant vs. background conversation",
                "Map utterances to allowed skill categories (timer, calendar, search, …)",
                "Respect user and org settings — which suggestions are enabled",
              ]}
            />
          </div>
          <p className="mt-4 text-[13px] leading-relaxed text-[var(--color-text-faint)]">
            This reduces false activations and limits what reaches generative
            models — a requirement for enterprise and regulated environments.
          </p>

          <SubHead>2.3 — Fulfillment layer</SubHead>
          <Lead>Qualified intents enter a structured fulfillment pipeline:</Lead>
          <div className="mt-4">
            <Bullets
              items={[
                "Select enabled skills and context (recent audio, screen snippets per policy)",
                "Call on-device AI (Gemini Nano / local model where available) against a fixed action schema — not open-ended chat",
                "Parse a single structured response (timer duration, event time, draft text)",
                "Apply confirmation policy before side effects (timers, calendar writes, messages)",
              ]}
            />
          </div>
          <p className="mt-4 text-[13px] leading-relaxed text-[var(--color-text-faint)]">
            Action families: timers, calendar events, lists, web search,
            document drafts, email/SMS drafts, navigation, music and
            research-style tasks — with explicit downgrade paths when a
            first-party API is unavailable.
          </p>

          <SubHead>2.4 — Surface layer (XR target)</SubHead>
          <Lead>
            Confirmations appear today as action cards on the phone. For
            Android XR, the same Ready state projects to the glasses viewport:
          </Lead>
          <div className="mt-4">
            <Bullets
              items={[
                "One primary action per card",
                "Readable typography and contrast for optical see-through",
                "Minimal animation; fast dismiss",
              ]}
            />
          </div>
          <p className="mt-4 text-[13px] leading-relaxed text-[var(--color-text-faint)]">
            Phone and glasses stay in sync: initiate on either, confirm where
            the user is looking.
          </p>
        </Section>

        {/* 03 — privacy */}
        <Section n="03" title="Privacy, security & enterprise controls">
          <DataTable
            headers={["Principle", "Implementation direction"]}
            rows={[
              [
                "Data minimization",
                "Qualification on-device before the LLM; no “send all audio to cloud” path for gating",
              ],
              [
                "User control",
                "Global on/off, snooze, active hours, per-skill suggest toggles",
              ],
              [
                "Encrypted settings",
                "Preferences stored in encrypted on-device storage",
              ],
              [
                "Auditability",
                "Fulfillment runs logged locally for debug/support",
              ],
              [
                "Confirmation for side effects",
                "Timers, calendar and messages require explicit user approval",
              ],
            ]}
          />
        </Section>

        {/* 04 — gemini strategy */}
        <Section n="04" title="Gemini & on-device AI strategy">
          <Lead>
            AgentV is built on-device first, with optional remote fallback for
            environments that allow it.
          </Lead>
          <div className="mt-5">
            <DataTable
              headers={["Tier", "Role"]}
              rows={[
                [
                  "Gemini Nano (AICore)",
                  "Preferred on supported Pixel / Android devices when available",
                ],
                [
                  "On-device Gemma (LiteRT-LM)",
                  "Fallback bundle for devices without AICore feature access",
                ],
                [
                  "Simulation / test mode",
                  "Deterministic responses for development and golden-path validation",
                ],
                [
                  "Remote inference (future)",
                  "Enterprise-hosted option with certificate pinning — not required for XR preview",
                ],
              ]}
            />
          </div>
          <p className="mt-5 text-[13px] leading-relaxed text-[var(--color-text-faint)]">
            AgentV is not “Gemini in a chat window.” The model powers
            structured fulfillment after qualification — producing JSON-like
            actions the app can render and execute.
          </p>
        </Section>

        {/* 05 — prototype status */}
        <Section n="05" title="Current prototype status (Pixel)">
          <Lead>
            A working end-to-end prototype on Pixel hardware, validated with
            automated golden tests.
          </Lead>
          <div className="mt-5">
            <DataTable
              headers={["Capability", "Status"]}
              rows={[
                [
                  "Voice → intent → structured fulfillment → action card",
                  <Status key="s" done label="Proven (device tests)" />,
                ],
                [
                  "Timer use case end-to-end",
                  <Status key="s" done label="Proven" />,
                ],
                [
                  "Audio fixture injection (WAV → speech → skill → card)",
                  <Status key="s" done label="Proven" />,
                ],
                [
                  "On-device LLM routing (local / hybrid)",
                  <Status key="s" done label="Proven on Pixel 10 class" />,
                ],
                [
                  "Glanceable ambient UI (idle / working / ready)",
                  <Status key="s" done label="Implemented on phone" />,
                ],
                [
                  "Projection to Gemini glasses",
                  <Status key="s" done={false} label="Target for XR program" />,
                ],
              ]}
            />
          </div>
          <p className="mt-5 text-[13px] leading-relaxed text-[var(--color-text-faint)]">
            Known gaps (honest scope): advanced audio-model accuracy on all
            phrases, the full glasses capture loop, and enterprise
            remote-inference hosting.
          </p>
        </Section>

        {/* 06 — XR plan */}
        <Section n="06" title="Android XR integration plan">
          <div className="grid gap-4 sm:grid-cols-3">
            {[
              [
                "Phase A",
                "Display parity (preview)",
                "Bind existing Ready action cards to the Android XR projected surface; sync dismissal and confirmation across phone ↔ glasses; tune layout for field of view.",
              ],
              [
                "Phase B",
                "Wearable capture",
                "Route the glasses microphone into the same qualification pipeline; treat glasses as a trusted capture source alongside phone and earbuds.",
              ],
              [
                "Phase C",
                "Enterprise pilots",
                "Configurable skill packs per vertical (healthcare, logistics, professional services); optional managed policy hooks for suggest/record flags.",
              ],
            ].map(([phase, title, body]) => (
              <div key={phase} className={`${PANEL} p-5`}>
                <div className="font-mono text-[11px] uppercase tracking-[0.2em] text-[var(--color-accent)]">
                  {phase}
                </div>
                <div className="mt-2 text-[14px] font-semibold text-[var(--color-text-primary)]">
                  {title}
                </div>
                <div className="mt-2 text-[12.5px] leading-relaxed text-[var(--color-text-secondary)]">
                  {body}
                </div>
              </div>
            ))}
          </div>
        </Section>

        {/* 07 — the ask */}
        <Section n="07" title="What we're asking from the program">
          <NumberedGrid
            items={[
              [
                "Early access",
                "Android XR projected UI and the Gemini glasses display path.",
              ],
              [
                "Guidance",
                "Recommended interaction patterns for confirmable cards in see-through optics.",
              ],
              [
                "Partnership",
                "Validate hands-busy workplace scenarios with real wearers.",
              ],
            ]}
          />
          <p className="mt-6 text-[14px] leading-relaxed text-[var(--color-text-secondary)]">
            We bring a running Pixel prototype, a clear privacy posture, and a
            product shape that fits glasses better than a phone chat app.
          </p>
        </Section>

        {/* 08 — appendix */}
        <Section n="08" title="Appendix — technical reference">
          <DataTable
            headers={["Document", "Contents"]}
            rows={[
              [
                "architecture.md",
                "Full system architecture — capture, gatekeeping, fulfillment, LLM",
              ],
              ["architecture-delta.md", "Contract rules and parity notes"],
              [
                "fm-ui-pixel-targets.md",
                "Visual targets for the ambient confirmation UI",
              ],
              ["blocker-register.md", "Active risks and exit criteria"],
              ["README.md", "Capability matrix and build/run instructions"],
            ]}
          />
        </Section>

        {/* footer */}
        <p className="mt-20 border-t border-white/[0.08] pt-8 text-[11px] italic leading-relaxed text-[var(--color-text-faint)]">
          AgentV is an independent reimplementation of ambient-assistant
          patterns for research and product exploration. This brief describes
          our intended experience on Android XR; it is not a Google product
          announcement.
        </p>
      </div>
    </section>
  );
}
