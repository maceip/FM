import { ActionButton, CardShell, CollapseButton } from "./CardShell";
import { DocumentIcon, PersonIcon, PhoneIcon, SendIcon } from "./icons";

export function ContactPersonContext() {
  return (
    <CardShell
      category="PeopleContext"
      title="Contact / Person Context"
      icon={<PersonIcon />}
      footer={
        <>
          <ActionButton icon={<DocumentIcon width={14} height={14} />} label="Notes" />
          <ActionButton icon={<PersonIcon width={14} height={14} />} label="History" />
          <CollapseButton />
        </>
      }
    >
      <div className="flex items-center gap-3">
        <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-[#27406e] to-[#0c1424]">
          <PersonIcon width={24} height={24} className="text-[var(--color-accent)]" />
        </div>
        <div className="min-w-0">
          <div className="flex items-center gap-1.5 text-[14px] font-semibold">
            Alex Morgan <span className="text-[var(--color-accent)]">★</span>
          </div>
          <div className="text-[11px] text-[var(--color-text-secondary)]">
            alex@quantumlab.com
          </div>
          <div className="text-[11px] text-[var(--color-text-secondary)]">
            +1 (415) 555-0198 · San Francisco, CA
          </div>
        </div>
      </div>

      <div className="mt-3 flex-1 rounded-lg border border-[var(--color-stroke)] bg-black/25 p-3">
        <div className="font-mono text-[9px] uppercase tracking-[0.16em] text-[var(--color-text-faint)]">
          Context
        </div>
        <p className="mt-1.5 text-[12px] leading-snug text-[var(--color-text-secondary)]">
          <span className="text-[var(--color-text-primary)]">
            Research collaborator
          </span>{" "}
          — working on quantum algorithms and benchmarking.
        </p>
      </div>

      <div className="mt-3 grid grid-cols-3 gap-2">
        {[
          ["Message", <SendIcon key="s" width={13} height={13} />],
          ["Call", <PhoneIcon key="p" width={13} height={13} />],
          ["More", <span key="m">···</span>],
        ].map(([label, ic]) => (
          <span
            key={label as string}
            className="flex items-center justify-center gap-1.5 rounded-lg border border-[var(--color-stroke-soft)] bg-black/20 py-2 font-mono text-[10px] uppercase tracking-[0.06em] text-[var(--color-text-secondary)]"
          >
            {ic}
            {label}
          </span>
        ))}
      </div>
    </CardShell>
  );
}
