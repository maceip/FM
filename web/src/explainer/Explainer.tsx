/**
 * The two-page AgentV explainer.
 *
 *  Page 1 — hero: the 3D card deck over the dune backdrop + positioning copy.
 *  Page 2 — the design document.
 *
 * Its own scroll container so it works regardless of the global body style.
 */
import { Background } from "../Background";
import { CardTextureProvider } from "../three/CardTextures";
import { HeroPage } from "./HeroPage";
import { DesignDoc } from "./DesignDoc";

function ExplainerLoader() {
  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: 18,
      }}
    >
      <div
        className="fm-loader-mark"
        style={{
          fontFamily: "var(--font-mono)",
          fontSize: 22,
          fontWeight: 700,
          letterSpacing: "0.28em",
          color: "var(--color-text-primary)",
        }}
      >
        AgentV
      </div>
      <div className="fm-loader-track" />
    </div>
  );
}

export function Explainer() {
  return (
    <div className="h-screen w-full overflow-x-hidden overflow-y-auto">
      <Background />
      <CardTextureProvider fallback={<ExplainerLoader />}>
        <div className="fm-rise relative">
          <HeroPage />
          <DesignDoc />
        </div>
      </CardTextureProvider>
    </div>
  );
}
