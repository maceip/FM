import { ActionButton, CardShell } from "./CardShell";
import { GlobeIcon, ShieldIcon } from "./icons";

export function ApproveUndo() {
  return (
    <CardShell
      category="PermissionConfirmation"
      title="Approve / Undo"
      icon={<ShieldIcon />}
      footer={
        <>
          <ActionButton icon={<span>☐</span>} label="Always Ask" />
          <ActionButton icon={<ShieldIcon width={14} height={14} />} label="Learn More" />
        </>
      }
    >
      <p className="text-[12px] leading-snug text-[var(--color-text-secondary)]">
        Allow{" "}
        <span className="text-[var(--color-text-primary)]">
          “Deep Research Agent”
        </span>{" "}
        to open the following link?
      </p>

      <div className="mt-3 flex items-center gap-3 rounded-lg border border-[var(--color-stroke)] bg-black/25 px-3 py-2.5">
        <GlobeIcon width={18} height={18} className="text-[var(--color-accent)]" />
        <div className="min-w-0">
          <div className="text-[12px] text-[var(--color-text-primary)]">
            IBM Quantum Computing Overview
          </div>
          <div className="truncate text-[10px] text-[var(--color-text-faint)]">
            https://www.ibm.com/quantum-computing
          </div>
        </div>
      </div>

      <div className="mt-3 grid grid-cols-3 gap-2 rounded-lg border border-[var(--color-stroke)] bg-black/20 p-2.5">
        {[
          ["Action", "Open Link"],
          ["Agent", "Deep Research Agent"],
          ["Time", "Just now"],
        ].map(([k, v]) => (
          <div key={k}>
            <div className="font-mono text-[8px] uppercase tracking-[0.14em] text-[var(--color-text-faint)]">
              {k}
            </div>
            <div className="mt-0.5 text-[10px] leading-tight text-[var(--color-text-secondary)]">
              {v}
            </div>
          </div>
        ))}
      </div>

      <div className="mt-3 flex gap-2">
        <span className="flex flex-[1.4] items-center justify-center gap-1.5 rounded-lg bg-[var(--color-accent-strong)] py-2.5 font-mono text-[11px] uppercase tracking-[0.08em] font-semibold text-white">
          ✓ Approve
        </span>
        <span className="flex flex-1 items-center justify-center gap-1.5 rounded-lg border border-[var(--color-stroke-soft)] py-2.5 font-mono text-[11px] uppercase tracking-[0.08em] text-[var(--color-text-secondary)]">
          ✕ Deny
        </span>
        <span className="flex flex-1 items-center justify-center gap-1.5 rounded-lg border border-[var(--color-stroke-soft)] py-2.5 font-mono text-[11px] uppercase tracking-[0.08em] text-[var(--color-text-secondary)]">
          ↺ Undo
        </span>
      </div>
    </CardShell>
  );
}
