import { ActionButton, CardShell, CollapseButton } from "./CardShell";
import { DocumentIcon, PhoneIcon, QuoteIcon } from "./icons";

const WAVE = Array.from({ length: 56 }, (_, i) =>
  6 + Math.abs(Math.sin(i * 0.7) * Math.cos(i * 0.31)) * 22,
);

export function LiveCallAgent() {
  return (
    <CardShell
      category="Calling"
      title="Live Call Agent"
      icon={<PhoneIcon />}
      footer={
        <>
          <ActionButton icon={<DocumentIcon width={14} height={14} />} label="Transcript" />
          <ActionButton icon={<QuoteIcon width={14} height={14} />} label="Summary" />
          <CollapseButton />
        </>
      }
    >
      <div className="rounded-xl border border-[var(--color-stroke)] bg-black/25 p-4">
        <div className="flex items-center gap-2 font-mono text-[11px] uppercase tracking-[0.16em]">
          <span className="h-2 w-2 rounded-full bg-[var(--color-good)]" />
          <span className="text-[var(--color-good)]">Connected</span>
          <span className="text-[var(--color-text-faint)]">· 01:24</span>
        </div>
        <div className="mt-3 flex h-10 items-center gap-[3px]">
          {WAVE.map((h, i) => (
            <span
              key={i}
              className="w-[3px] rounded-full bg-[var(--color-accent)]"
              style={{ height: h, opacity: 0.45 + (h / 28) * 0.55 }}
            />
          ))}
        </div>
        <div className="mt-3 flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-full bg-[var(--color-accent-strong)] font-mono text-[12px] font-bold">
            AI
          </div>
          <div>
            <div className="text-[13px] font-semibold">AI Agent</div>
            <div className="text-[11px] text-[var(--color-text-secondary)]">
              Your research assistant
            </div>
          </div>
        </div>
      </div>

      <div className="mt-3 grid grid-cols-4 gap-2">
        {["Mute", "Keypad", "Hold"].map((l) => (
          <div
            key={l}
            className="flex flex-col items-center gap-1 rounded-lg border border-[var(--color-stroke-soft)] bg-black/20 py-2 text-[10px] text-[var(--color-text-secondary)]"
          >
            <span className="h-4 w-4 rounded-sm border border-[var(--color-text-faint)]" />
            {l}
          </div>
        ))}
        <div className="flex flex-col items-center gap-1 rounded-lg border border-[var(--color-danger)]/40 bg-[var(--color-danger)]/15 py-2 text-[10px] text-[var(--color-danger)]">
          <PhoneIcon width={16} height={16} />
          End
        </div>
      </div>
    </CardShell>
  );
}
