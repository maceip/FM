import { ActionButton, CardShell, CollapseButton } from "./CardShell";
import { CalendarIcon, DocumentIcon, PlusIcon, SendIcon } from "./icons";

export function DraftSend() {
  return (
    <CardShell
      category="Messaging"
      title="Draft + Send"
      icon={<SendIcon />}
      footer={
        <>
          <ActionButton icon={<CalendarIcon width={14} height={14} />} label="Schedule" />
          <ActionButton icon={<DocumentIcon width={14} height={14} />} label="Save Draft" />
          <CollapseButton />
        </>
      }
    >
      <div className="flex items-center gap-3 rounded-lg border border-[var(--color-stroke)] bg-black/25 px-3 py-2">
        <span className="font-mono text-[10px] uppercase tracking-[0.16em] text-[var(--color-text-faint)]">
          To
        </span>
        <span className="flex-1 text-[12px] text-[var(--color-text-primary)]">
          alex@quantumlab.com
        </span>
        <PlusIcon width={15} height={15} className="text-[var(--color-text-faint)]" />
      </div>

      <div className="mt-3 flex-1 rounded-lg border border-[var(--color-stroke)] bg-black/25 p-3 text-[12px] leading-relaxed text-[var(--color-text-secondary)]">
        <p className="text-[var(--color-text-primary)]">Hi Alex,</p>
        <p className="mt-2">
          Here are the key findings from my research on quantum computing.
          Let me know your thoughts.
        </p>
      </div>

      <div className="mt-3 flex items-center gap-2">
        <span className="flex h-8 w-8 items-center justify-center rounded-md border border-[var(--color-stroke-soft)] text-[var(--color-text-faint)]">
          @
        </span>
        <span className="flex h-8 w-8 items-center justify-center rounded-md border border-[var(--color-stroke-soft)] text-[var(--color-text-faint)]">
          ☺
        </span>
        <span className="flex h-8 items-center gap-1.5 rounded-md border border-[var(--color-stroke-soft)] px-2.5 font-mono text-[10px] uppercase tracking-[0.08em] text-[var(--color-text-secondary)]">
          AI Assist
        </span>
        <span className="ml-auto flex h-8 items-center gap-1.5 rounded-md bg-[var(--color-accent-strong)] px-3.5 font-mono text-[11px] uppercase tracking-[0.08em] font-semibold text-white">
          <SendIcon width={13} height={13} />
          Send
        </span>
      </div>
    </CardShell>
  );
}
