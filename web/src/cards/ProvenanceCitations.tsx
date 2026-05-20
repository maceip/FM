import { ActionButton, CardShell, CollapseButton, Tabs } from "./CardShell";
import { DocumentIcon, QuoteIcon, SendIcon } from "./icons";

const SOURCES = [
  ["1", "What is Quantum Computing? | IBM", "ibm.com/quantum-computing/learn"],
  ["2", "A Gentle Introduction to Qubits", "qiskit.org/learn/intro-to-qubits"],
  ["▶", "Quantum Computing Explained Simply", "youtube.com/watch?v=…"],
];

export function ProvenanceCitations() {
  return (
    <CardShell
      category="SourceCitation"
      title="Provenance / Citations"
      icon={<QuoteIcon />}
      footer={
        <>
          <ActionButton icon={<SendIcon width={14} height={14} />} label="Export" />
          <ActionButton icon={<DocumentIcon width={14} height={14} />} label="Copy All" />
          <CollapseButton />
        </>
      }
    >
      <Tabs tabs={["Sources", "Provenance"]} active={0} />
      <ul className="mt-3 flex-1 space-y-2">
        {SOURCES.map(([n, title, url]) => (
          <li
            key={url}
            className="flex items-center gap-3 rounded-lg border border-[var(--color-stroke)] bg-black/25 px-3 py-2"
          >
            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-black/40 font-mono text-[11px] text-[var(--color-accent)]">
              {n}
            </span>
            <div className="min-w-0 flex-1">
              <div className="truncate text-[12px] text-[var(--color-text-primary)]">
                {title}
              </div>
              <div className="truncate text-[10px] text-[var(--color-text-faint)]">
                {url}
              </div>
            </div>
            <span className="text-[var(--color-text-faint)]">↗</span>
          </li>
        ))}
      </ul>
    </CardShell>
  );
}
