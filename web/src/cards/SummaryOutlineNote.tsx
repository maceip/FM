import { ActionButton, CardShell, CollapseButton, Tabs } from "./CardShell";
import { DocumentIcon, SendIcon } from "./icons";

const BULLETS = [
  "Qubits leverage superposition and entanglement to process information.",
  "Major approaches include gate-based, annealing, and photonic systems.",
  "Key challenges: error correction, scalability, and decoherence.",
  "Applications span cryptography, simulation, and optimization.",
];

export function SummaryOutlineNote() {
  return (
    <CardShell
      category="Document"
      title="Summary / Outline / Note"
      icon={<DocumentIcon />}
      footer={
        <>
          <ActionButton icon={<DocumentIcon width={14} height={14} />} label="Copy" />
          <ActionButton icon={<SendIcon width={14} height={14} />} label="Export" />
          <CollapseButton />
        </>
      }
    >
      <Tabs tabs={["Summary", "Outline", "Note"]} active={0} />
      <div className="mt-3 flex-1 rounded-xl border border-[var(--color-stroke)] bg-black/25 p-3.5">
        <h3 className="text-[13px] font-semibold text-[var(--color-text-primary)]">
          Quantum Computing Overview
        </h3>
        <ul className="mt-2 space-y-1.5">
          {BULLETS.map((b) => (
            <li
              key={b}
              className="flex gap-2 text-[11px] leading-snug text-[var(--color-text-secondary)]"
            >
              <span className="mt-1 h-1 w-1 shrink-0 rounded-full bg-[var(--color-accent)]" />
              {b}
            </li>
          ))}
        </ul>
      </div>
    </CardShell>
  );
}
