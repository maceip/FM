/**
 * Shared chrome for every FM card: header (icon tile + category + title),
 * a flexible content region, and a footer action row.
 *
 * Each card renders at a fixed pixel size so it can be rasterised to a
 * power-of-two-friendly texture and mapped onto a mesh in the R3F scene.
 */
import type { ReactNode } from "react";
import { CollapseIcon, KebabIcon } from "./icons";

export const CARD_WIDTH = 460;
export const CARD_HEIGHT = 412;
export const BROWSER_WIDTH = 460;
export const BROWSER_HEIGHT = 612;

interface CardShellProps {
  category: string;
  title: ReactNode;
  icon: ReactNode;
  accent?: string;
  /** Status-dot colour next to the category label. Defaults to the accent. */
  dot?: string;
  width?: number;
  height?: number;
  children: ReactNode;
  footer: ReactNode;
}

export function CardShell({
  category,
  title,
  icon,
  accent = "var(--color-accent)",
  dot,
  width = CARD_WIDTH,
  height = CARD_HEIGHT,
  children,
  footer,
}: CardShellProps) {
  return (
    <div
      className="relative flex flex-col overflow-hidden rounded-[20px] border border-[var(--color-stroke)] bg-gradient-to-b from-[var(--color-card-top)] to-[var(--color-card-bottom)]"
      style={{ width, height }}
    >
      {/* faint inner top highlight, mirrors the screenshots */}
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-white/10" />

      <header className="flex items-start gap-3 px-5 pt-5">
        <div
          className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl border border-[var(--color-stroke)] bg-black/30"
          style={{ color: accent }}
        >
          {icon}
        </div>
        <div className="min-w-0 flex-1 pt-0.5">
          <div className="flex items-center gap-2">
            <span
              className="h-2 w-2 rounded-full"
              style={{ background: dot ?? accent }}
            />
            <span className="font-mono text-[11px] uppercase tracking-[0.18em] text-[var(--color-text-secondary)]">
              {category}
            </span>
          </div>
          <h2 className="mt-1.5 font-mono text-[20px] font-semibold uppercase leading-tight tracking-[0.04em] text-[var(--color-text-primary)]">
            {title}
          </h2>
        </div>
        <button
          type="button"
          className="-mr-1 -mt-1 shrink-0 text-[var(--color-text-faint)]"
          aria-label="More"
        >
          <KebabIcon width={18} height={18} />
        </button>
      </header>

      <div className="flex min-h-0 flex-1 flex-col px-5 pt-4">{children}</div>

      <footer className="mt-auto flex items-center gap-2 border-t border-[var(--color-stroke)] px-4 py-3.5">
        {footer}
      </footer>
    </div>
  );
}

interface ActionButtonProps {
  icon: ReactNode;
  label: string;
  /** Right-most "Collapse to Edge" treatment. */
  collapse?: boolean;
}

export function ActionButton({ icon, label, collapse }: ActionButtonProps) {
  return (
    <button
      type="button"
      className="flex flex-1 items-center justify-center gap-1.5 rounded-lg border border-[var(--color-stroke-soft)] bg-black/20 py-2.5 font-mono text-[10px] uppercase tracking-[0.08em] text-[var(--color-text-secondary)]"
    >
      <span className="text-[var(--color-text-faint)]">
        {collapse ? <CollapseIcon width={14} height={14} /> : icon}
      </span>
      {label}
    </button>
  );
}

export function CollapseButton() {
  return <ActionButton icon={null} label="Collapse to Edge" collapse />;
}

/** Horizontal tab strip used by several cards (Summary / Outline / Note …). */
export function Tabs({
  tabs,
  active = 0,
}: {
  tabs: string[];
  active?: number;
}) {
  return (
    <div className="flex gap-5 border-b border-[var(--color-stroke)] pb-2">
      {tabs.map((t, i) => (
        <span
          key={t}
          className={
            "font-mono text-[11px] uppercase tracking-[0.12em] " +
            (i === active
              ? "text-[var(--color-accent)]"
              : "text-[var(--color-text-faint)]")
          }
        >
          {t}
        </span>
      ))}
    </div>
  );
}
