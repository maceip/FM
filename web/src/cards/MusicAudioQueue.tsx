import { ActionButton, CardShell, CollapseButton } from "./CardShell";
import { ChecklistIcon, MusicIcon } from "./icons";

const QUEUE = [
  ["2", "Entanglement", "4:18"],
  ["3", "Superposition", "3:02"],
];

export function MusicAudioQueue() {
  return (
    <CardShell
      category="MediaPlayback"
      title="Music / Audio Queue"
      icon={<MusicIcon />}
      footer={
        <>
          <ActionButton icon={<ChecklistIcon width={14} height={14} />} label="Queue" />
          <ActionButton icon={<MusicIcon width={14} height={14} />} label="Devices" />
          <CollapseButton />
        </>
      }
    >
      <div className="flex items-center gap-3">
        <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-[#1d2b56] to-[#0a0f1e]">
          <MusicIcon width={22} height={22} className="text-[var(--color-accent)]" />
        </div>
        <div className="min-w-0">
          <div className="truncate text-[13px] font-semibold">Interstellar Minds</div>
          <div className="truncate text-[11px] text-[var(--color-text-secondary)]">
            The Quantum State
          </div>
        </div>
      </div>

      <div className="mt-3">
        <div className="h-1 overflow-hidden rounded-full bg-black/40">
          <div className="h-full w-[40%] rounded-full bg-[var(--color-accent)]" />
        </div>
        <div className="mt-1 flex justify-between font-mono text-[9px] text-[var(--color-text-faint)]">
          <span>1:32</span>
          <span>3:45</span>
        </div>
      </div>

      <div className="mt-1 flex items-center justify-center gap-5 text-[var(--color-text-secondary)]">
        <span className="text-[13px]">⇄</span>
        <span className="text-[15px]">⏮</span>
        <span className="flex h-9 w-9 items-center justify-center rounded-full bg-white text-[14px] text-black">
          ⏸
        </span>
        <span className="text-[15px]">⏭</span>
        <span className="text-[13px]">↻</span>
      </div>

      <div className="mt-2 flex-1 rounded-lg border border-[var(--color-stroke)] bg-black/25 p-2">
        <div className="font-mono text-[9px] uppercase tracking-[0.16em] text-[var(--color-text-faint)]">
          Up Next
        </div>
        {QUEUE.map(([n, title, dur]) => (
          <div key={n} className="mt-1.5 flex items-center gap-2 text-[11px]">
            <span className="text-[var(--color-text-faint)]">{n}</span>
            <span className="flex-1 text-[var(--color-text-primary)]">{title}</span>
            <span className="font-mono text-[10px] text-[var(--color-text-faint)]">
              {dur}
            </span>
          </div>
        ))}
      </div>
    </CardShell>
  );
}
