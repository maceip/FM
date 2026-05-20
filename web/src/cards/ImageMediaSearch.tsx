import { ActionButton, CardShell, CollapseButton } from "./CardShell";
import { DocumentIcon, ImageIcon } from "./icons";

const TABS = ["All", "Images", "Videos", "News", "GIFs"];
const SWATCHES = ["#3b2a1a", "#142a4d", "#2a1d4d"];

export function ImageMediaSearch() {
  return (
    <CardShell
      category="MediaImage"
      title="Image / Media Search"
      icon={<ImageIcon />}
      footer={
        <>
          <ActionButton icon={<ImageIcon width={14} height={14} />} label="Open" />
          <ActionButton icon={<DocumentIcon width={14} height={14} />} label="Save" />
          <CollapseButton />
        </>
      }
    >
      <div className="flex items-center gap-2 rounded-lg border border-[var(--color-stroke)] bg-black/25 px-3 py-2">
        <span className="text-[var(--color-text-faint)]">⌕</span>
        <span className="flex-1 text-[12px] text-[var(--color-text-primary)]">
          quantum computer
        </span>
        <span className="text-[var(--color-text-faint)]">✕</span>
      </div>

      <div className="mt-3 flex gap-2">
        {TABS.map((t, i) => (
          <span
            key={t}
            className={
              "rounded-md px-2.5 py-1 font-mono text-[9px] uppercase tracking-[0.06em] " +
              (i === 1
                ? "bg-[var(--color-accent-strong)] text-white"
                : "border border-[var(--color-stroke-soft)] text-[var(--color-text-faint)]")
            }
          >
            {t}
          </span>
        ))}
      </div>

      <div className="mt-3 grid flex-1 grid-cols-3 gap-2">
        {SWATCHES.map((c) => (
          <div
            key={c}
            className="flex items-center justify-center rounded-lg border border-[var(--color-stroke)]"
            style={{
              background: `radial-gradient(circle at 50% 40%, ${c}, #07090f)`,
            }}
          >
            <ImageIcon width={20} height={20} className="text-white/30" />
          </div>
        ))}
      </div>
    </CardShell>
  );
}
