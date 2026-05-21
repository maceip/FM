/**
 * Marketing-site entry point.
 *
 * 1. CardTextureProvider rasterises all 12 DOM cards into textures.
 * 2. Experience (outside the Canvas) reads those textures from context and
 *    hands them to the scene as a plain prop — React context does not cross
 *    the R3F renderer boundary, so the prop bridge is deliberate.
 * 3. StackScene drives the scroll animation.
 */
import { useState } from "react";
import { Canvas } from "@react-three/fiber";
import { ScrollControls } from "@react-three/drei";
import { CardTextureProvider, useCardTextures } from "./three/CardTextures";
import { StackScene } from "./three/StackScene";

/**
 * Photographic backdrop behind the transparent Canvas.
 *
 * The dune photo lives at /background.webp (public/). The gradient fallback
 * approximates its mood — near-black sky, cool rim-light glow low-left — and
 * shows if the image ever fails to load.
 */
function Background() {
  const [hasPhoto, setHasPhoto] = useState(true);
  return (
    <div className="fixed inset-0 -z-10" aria-hidden>
      <div className="scene-bg-gradient absolute inset-0" />
      {hasPhoto && (
        <img
          src="/background.webp"
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          onError={() => setHasPhoto(false)}
        />
      )}
      {/* vignette + floor lift so the card deck keeps its contrast */}
      <div className="scene-bg-veil absolute inset-0" />
    </div>
  );
}

function Loader() {
  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        display: "grid",
        placeItems: "center",
        color: "var(--color-text-secondary)",
        fontFamily: "var(--font-mono)",
        fontSize: 12,
        letterSpacing: "0.2em",
        textTransform: "uppercase",
      }}
    >
      Rendering cards…
    </div>
  );
}

function Overlay() {
  return (
    <div className="pointer-events-none fixed inset-0">
      <div className="absolute left-8 top-7">
        <div className="font-mono text-[13px] font-bold tracking-[0.3em] text-[var(--color-text-primary)]">
          FM
        </div>
        <div className="mt-1 max-w-[230px] text-[12px] leading-snug text-[var(--color-text-secondary)]">
          An ambient agent with local on-device intelligence.
        </div>
      </div>
      <div className="absolute bottom-7 left-1/2 -translate-x-1/2 font-mono text-[10px] uppercase tracking-[0.24em] text-[var(--color-text-faint)]">
        Scroll to flip ↓
      </div>
    </div>
  );
}

function Experience() {
  const textures = useCardTextures();
  return (
    <>
      <Background />
      <Canvas
        camera={{ position: [0, 1.6, 5.2], fov: 40 }}
        gl={{ antialias: true, alpha: true }}
        dpr={[1, 2]}
        style={{ position: "relative" }}
      >
        {/* Lights shade the card bodies so their thickness reads; the
            textured faces are unlit (meshBasicMaterial) and stay crisp. */}
        <ambientLight intensity={1.1} />
        <directionalLight position={[3, 5, 4]} intensity={2.2} />
        <directionalLight position={[-4, -2, 2]} intensity={0.6} color="#4d8dff" />
        <ScrollControls pages={2.4} damping={0.22}>
          <StackScene textures={textures} />
        </ScrollControls>
      </Canvas>
      <Overlay />
    </>
  );
}

export default function App() {
  return (
    <CardTextureProvider fallback={<Loader />}>
      <Experience />
    </CardTextureProvider>
  );
}
