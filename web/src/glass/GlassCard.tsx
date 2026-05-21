/**
 * GlassCard — translucent "fake card" used to intersperse copy between the
 * real 3D card assets. Five size variants; optional frosted header.
 *
 *   <GlassCard variant="wide" />                        empty panel
 *   <GlassCard variant="pill" headerTitle="CARD TITLE">…</GlassCard>
 *
 * Needs a dark backdrop behind it or the translucency won't read.
 */
import type { HTMLAttributes, ReactNode } from "react";
import "./glass-card.css";

export type GlassCardVariant =
  | "overview"
  | "wide"
  | "pill"
  | "square"
  | "portrait";

type GlassCardProps = HTMLAttributes<HTMLElement> & {
  variant?: GlassCardVariant;
  headerTitle?: ReactNode;
  children?: ReactNode;
};

export function GlassCard({
  variant = "wide",
  headerTitle,
  children,
  className = "",
  ...props
}: GlassCardProps) {
  const hasHeader = Boolean(headerTitle);

  return (
    <section
      className={[
        "glass-card",
        `glass-card--${variant}`,
        hasHeader ? "glass-card--with-header" : "",
        className,
      ]
        .filter(Boolean)
        .join(" ")}
      {...props}
    >
      <div className="glass-card__surface">
        {hasHeader && (
          <div className="glass-card__header">
            <div className="glass-card__header-title">{headerTitle}</div>
          </div>
        )}

        <div className="glass-card__content">{children}</div>
      </div>
    </section>
  );
}
