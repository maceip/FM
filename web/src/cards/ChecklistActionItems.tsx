import { ActionButton, CardShell, CollapseButton } from "./CardShell";
import { ChecklistIcon, PlusIcon } from "./icons";

const ITEMS: [string, boolean][] = [
  ["Review quantum algorithms", true],
  ["Analyze hardware benchmarks", true],
  ["Compare error correction methods", false],
  ["Evaluate scalability metrics", false],
  ["Draft research summary", true],
  ["Prepare presentation", false],
  ["Follow up with Alex", false],
];

export function ChecklistActionItems() {
  const done = ITEMS.filter(([, c]) => c).length;
  return (
    <CardShell
      category="ListTasks"
      title="Checklist / Action Items"
      icon={<ChecklistIcon />}
      footer={
        <>
          <ActionButton icon={<PlusIcon width={14} height={14} />} label="Add Task" />
          <ActionButton icon={<ChecklistIcon width={14} height={14} />} label="Reorder" />
          <CollapseButton />
        </>
      }
    >
      <div className="flex items-center gap-3">
        <span className="font-mono text-[10px] uppercase tracking-[0.14em] text-[var(--color-text-secondary)]">
          {done} of {ITEMS.length} completed
        </span>
        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-black/40">
          <div
            className="h-full rounded-full bg-[var(--color-accent)]"
            style={{ width: `${(done / ITEMS.length) * 100}%` }}
          />
        </div>
      </div>

      <ul className="mt-3 space-y-1.5">
        {ITEMS.map(([label, checked]) => (
          <li key={label} className="flex items-center gap-2.5 text-[12px]">
            <span
              className={
                "flex h-4 w-4 shrink-0 items-center justify-center rounded-[5px] border " +
                (checked
                  ? "border-[var(--color-accent)] bg-[var(--color-accent)] text-white"
                  : "border-[var(--color-text-faint)]")
              }
            >
              {checked && (
                <svg viewBox="0 0 12 12" width={9} height={9} fill="none">
                  <path
                    d="m2 6 2.5 2.5L10 3"
                    stroke="currentColor"
                    strokeWidth={2}
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              )}
            </span>
            <span
              className={
                checked
                  ? "text-[var(--color-text-faint)] line-through"
                  : "text-[var(--color-text-primary)]"
              }
            >
              {label}
            </span>
          </li>
        ))}
      </ul>
    </CardShell>
  );
}
