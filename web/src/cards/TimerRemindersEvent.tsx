import { ActionButton, CardShell, CollapseButton, Tabs } from "./CardShell";
import { CalendarIcon, PlusIcon } from "./icons";

export function TimerRemindersEvent() {
  return (
    <CardShell
      category="CalendarTime"
      title="Timer / Reminders / Event"
      icon={<CalendarIcon />}
      footer={
        <>
          <ActionButton icon={<PlusIcon width={14} height={14} />} label="Add Reminder" />
          <ActionButton icon={<CalendarIcon width={14} height={14} />} label="Create Event" />
          <CollapseButton />
        </>
      }
    >
      <Tabs tabs={["Timer", "Reminders", "Event"]} active={0} />
      <div className="mt-3 flex flex-1 flex-col items-center justify-center rounded-xl border border-[var(--color-stroke)] bg-black/25 py-4">
        <span className="font-mono text-[10px] uppercase tracking-[0.18em] text-[var(--color-text-secondary)]">
          Focus Session
        </span>
        <div className="mt-1 flex items-end gap-3 font-mono">
          <span className="text-[44px] font-bold leading-none tracking-tight text-[var(--color-text-primary)]">
            25
          </span>
          <span className="pb-1 text-[44px] font-bold leading-none text-[var(--color-text-faint)]">
            :
          </span>
          <span className="text-[44px] font-bold leading-none tracking-tight text-[var(--color-text-primary)]">
            00
          </span>
        </div>
        <div className="flex gap-[52px] font-mono text-[9px] uppercase tracking-[0.2em] text-[var(--color-text-faint)]">
          <span>Min</span>
          <span>Sec</span>
        </div>
      </div>
      <div className="mt-3 flex gap-2">
        <span className="flex flex-1 items-center justify-center gap-2 rounded-lg bg-[var(--color-accent-strong)] py-2.5 font-mono text-[12px] uppercase tracking-[0.1em] font-semibold text-white">
          ▶ Start
        </span>
        <span className="flex w-11 items-center justify-center rounded-lg border border-[var(--color-stroke-soft)] text-[var(--color-text-faint)]">
          ↻
        </span>
      </div>
    </CardShell>
  );
}
