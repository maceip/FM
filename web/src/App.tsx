/**
 * Marketing-site entry point.
 *
 * 1. CardTextureProvider rasterises all 12 DOM cards into textures.
 * 2. Experience (outside the Canvas) reads those textures from context and
 *    hands them to the scene as a plain prop — React context does not cross
 *    the R3F renderer boundary, so the prop bridge is deliberate.
 * 3. StackScene drives the scroll animation.
 *
 * /#orbs and /#tab swap in showcases for reviewing those core components.
 */
import { Canvas } from "@react-three/fiber";
import { ScrollControls } from "@react-three/drei";
import { Background } from "./Background";
import { CardTextureProvider, useCardTextures } from "./three/CardTextures";
import { StackScene } from "./three/StackScene";
import { OrbShowcase } from "./three/OrbShowcase";
import { TabShowcase } from "./three/TabShowcase";

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
  if (window.location.hash === "#orbs") return <OrbShowcase />;
  if (window.location.hash === "#tab") return <TabShowcase />;
  return (
    <CardTextureProvider fallback={<Loader />}>
      <Experience />
    </CardTextureProvider>
  );
}
