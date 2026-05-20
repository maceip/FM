import { ActionButton, CardShell, CollapseButton } from "./CardShell";
import { ChecklistIcon, NavigationIcon } from "./icons";

const MODES = ["Car", "Transit", "Walk", "Bike"];

export function RouteAssist() {
  return (
    <CardShell
      category="Navigation"
      title="Route Assist"
      icon={<NavigationIcon />}
      footer={
        <>
          <ActionButton icon={<NavigationIcon width={14} height={14} />} label="Alternatives" />
          <ActionButton icon={<ChecklistIcon width={14} height={14} />} label="Steps" />
          <CollapseButton />
        </>
      }
    >
      <div className="space-y-1.5">
        {[
          ["From", "Current Location"],
          ["To", "Quantum Lab, 1600 Amphitheatre Pkwy"],
        ].map(([k, v]) => (
          <div
            key={k}
            className="flex items-center gap-3 rounded-lg border border-[var(--color-stroke)] bg-black/25 px-3 py-1.5"
          >
            <span className="w-9 font-mono text-[9px] uppercase tracking-[0.12em] text-[var(--color-text-faint)]">
              {k}
            </span>
            <span className="truncate text-[11px] text-[var(--color-text-primary)]">
              {v}
            </span>
          </div>
        ))}
      </div>

      <div className="mt-2 grid grid-cols-4 gap-1.5">
        {MODES.map((m, i) => (
          <span
            key={m}
            className={
              "rounded-md py-1 text-center font-mono text-[9px] uppercase tracking-[0.06em] " +
              (i === 0
                ? "bg-[var(--color-accent-strong)] text-white"
                : "border border-[var(--color-stroke-soft)] text-[var(--color-text-faint)]")
            }
          >
            {m}
          </span>
        ))}
      </div>

      <div className="relative mt-2 flex-1 overflow-hidden rounded-lg border border-[var(--color-stroke)] bg-[#0a1322]">
        <svg className="absolute inset-0 h-full w-full" viewBox="0 0 300 90">
          <path
            d="M20 70 C 70 70 70 30 130 30 S 220 60 280 22"
            fill="none"
            stroke="var(--color-accent)"
            strokeWidth={4}
            strokeLinecap="round"
          />
          <circle cx="20" cy="70" r="5" fill="var(--color-text-faint)" />
          <circle cx="280" cy="22" r="6" fill="var(--color-danger)" />
        </svg>
      </div>

      <div className="mt-2 flex items-center gap-3">
        <div>
          <span className="font-mono text-[15px] font-bold text-[var(--color-good)]">
            24 min
          </span>
          <div className="text-[10px] text-[var(--color-text-secondary)]">
            12.4 mi · Light traffic
          </div>
        </div>
        <span className="ml-auto flex items-center gap-1.5 rounded-lg bg-[var(--color-accent-strong)] px-4 py-2 font-mono text-[11px] uppercase tracking-[0.08em] font-semibold text-white">
          ▶ Start
        </span>
      </div>
    </CardShell>
  );
}
