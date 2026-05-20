import {
  ActionButton,
  BROWSER_HEIGHT,
  BROWSER_WIDTH,
  CardShell,
  CollapseButton,
  Tabs,
} from "./CardShell";
import { GlobeIcon, ImageIcon } from "./icons";

/**
 * The "12th card" — rendered larger than the grid cards. Shown as a hero in
 * the second screenshot and used as the focal card in the scroll animation.
 */
export function MiniBrowser() {
  return (
    <CardShell
      category="Browser"
      title="Mini Browser"
      icon={<GlobeIcon />}
      width={BROWSER_WIDTH}
      height={BROWSER_HEIGHT}
      footer={
        <>
          <ActionButton icon={<GlobeIcon width={14} height={14} />} label="Share" />
          <ActionButton icon={<span>⌕</span>} label="Find on Page" />
          <CollapseButton />
        </>
      }
    >
      <div className="flex items-center gap-2 rounded-lg border border-[var(--color-stroke)] bg-black/30 px-3 py-2">
        <span className="text-[var(--color-text-faint)]">🔒</span>
        <span className="flex-1 truncate font-mono text-[11px] text-[var(--color-text-primary)]">
          https://quantumlab.com/article/quantum-breakthrough
        </span>
        <span className="text-[var(--color-text-faint)]">≡</span>
      </div>

      <div className="mt-3">
        <Tabs tabs={["Search", "Reader", "Live Page"]} active={0} />
      </div>

      <div className="mt-3 flex-1 overflow-hidden rounded-xl border border-[var(--color-stroke)] bg-black/30 p-3">
        <div className="flex items-center gap-2 border-b border-[var(--color-stroke-soft)] pb-2">
          <GlobeIcon width={14} height={14} className="text-[var(--color-accent)]" />
          <span className="font-mono text-[10px] uppercase tracking-[0.12em] text-[var(--color-text-secondary)]">
            QuantumLab
          </span>
        </div>
        <div className="mt-2 font-mono text-[8px] uppercase tracking-[0.14em] text-[var(--color-accent)]">
          Research › Quantum Computing
        </div>
        <div className="mt-1.5 flex gap-3">
          <div className="min-w-0 flex-1">
            <h3 className="text-[14px] font-semibold leading-tight text-[var(--color-text-primary)]">
              Quantum Breakthrough: New Qubit Stability Record
            </h3>
            <p className="mt-1.5 text-[10px] leading-snug text-[var(--color-text-secondary)]">
              Researchers achieve unprecedented qubit coherence, bringing
              fault-tolerant quantum computing closer to reality.
            </p>
          </div>
          <div className="flex h-16 w-20 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-[#16315e] to-[#070b14]">
            <ImageIcon width={20} height={20} className="text-white/30" />
          </div>
        </div>
        <div className="mt-2 font-mono text-[8px] uppercase tracking-[0.14em] text-[var(--color-accent)]">
          Key Findings
        </div>
        <div className="mt-1.5 grid grid-cols-2 gap-2">
          {[
            ["10x Longer Coherence", "Qubits stay stable up to 10x longer than prior records."],
            ["Error Threshold Up", "Lower error rates pave the way for scalable systems."],
          ].map(([t, d]) => (
            <div
              key={t}
              className="rounded-lg border border-[var(--color-stroke-soft)] bg-black/30 p-2"
            >
              <div className="text-[10px] font-semibold text-[var(--color-text-primary)]">
                {t}
              </div>
              <div className="mt-0.5 text-[9px] leading-snug text-[var(--color-text-faint)]">
                {d}
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="mt-3 grid grid-cols-4 gap-2">
        {["← Back", "→ Forward", "↻ Refresh", "↗ Open Full"].map((l) => (
          <span
            key={l}
            className="rounded-lg border border-[var(--color-stroke-soft)] bg-black/20 py-2 text-center font-mono text-[9px] uppercase tracking-[0.04em] text-[var(--color-text-secondary)]"
          >
            {l}
          </span>
        ))}
      </div>
    </CardShell>
  );
}
