/**
 * "Deal With It" easter egg.
 *
 * The Android robot, with pixel-art sunglasses that fall from the sky and
 * bounce onto its face — then "DEAL WITH IT" snaps in. Triggered by clicking
 * the AgentV wordmark on the hero; click anywhere to dismiss.
 */

/** Classic blocky 8-bit sunglasses. */
function PixelShades() {
  return (
    <svg
      viewBox="0 0 340 132"
      width="100%"
      shapeRendering="crispEdges"
      aria-hidden
    >
      <g fill="#0a0a0c">
        {/* brow bar */}
        <rect x="14" y="14" width="312" height="26" />
        {/* outer pixel steps */}
        <rect x="0" y="26" width="14" height="14" />
        <rect x="326" y="26" width="14" height="14" />
        {/* left lens */}
        <rect x="28" y="40" width="126" height="60" />
        <rect x="40" y="100" width="102" height="12" />
        {/* right lens */}
        <rect x="186" y="40" width="126" height="60" />
        <rect x="198" y="100" width="102" height="12" />
      </g>
      {/* lens glints */}
      <g fill="#3b6fd6">
        <rect x="40" y="52" width="26" height="10" />
        <rect x="198" y="52" width="26" height="10" />
      </g>
    </svg>
  );
}

export function DealWithIt({ onClose }: { onClose: () => void }) {
  return (
    <div
      className="dwi-overlay"
      onClick={onClose}
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 60,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: 28,
        background: "rgba(4,6,12,0.86)",
        cursor: "pointer",
      }}
    >
      <div style={{ position: "relative", width: 620, maxWidth: "88vw" }}>
        <img
          src="/robot.webp"
          alt="Android robot"
          draggable={false}
          style={{
            display: "block",
            width: "100%",
            borderRadius: 20,
            boxShadow: "0 30px 80px rgba(0,0,0,0.6)",
          }}
        />
        {/* sunglasses — fall from the sky onto the robot's eyes */}
        <div
          className="dwi-glasses"
          style={{
            position: "absolute",
            left: "18%",
            top: "29%",
            width: "64%",
          }}
        >
          <PixelShades />
        </div>
      </div>

      <div
        className="dwi-text"
        style={{
          fontFamily: "var(--font-sans)",
          fontSize: "clamp(38px, 6.5vw, 84px)",
          fontWeight: 900,
          letterSpacing: "0.01em",
          color: "#ffffff",
          textShadow: "5px 5px 0 rgba(0,0,0,0.55)",
        }}
      >
        DEAL WITH IT
      </div>

      <div
        style={{
          fontFamily: "var(--font-mono)",
          fontSize: 10,
          letterSpacing: "0.24em",
          textTransform: "uppercase",
          color: "var(--color-text-faint)",
        }}
      >
        Click anywhere to dismiss
      </div>
    </div>
  );
}
