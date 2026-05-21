/**
 * Explainer — page 1. A full-viewport hero: the 3D card deck floating over
 * the dune backdrop, with the AgentV positioning copy.
 */
import { useRef } from "react";
import { Canvas, useFrame } from "@react-three/fiber";
import * as THREE from "three";
import { CARDS } from "../cards/registry";
import { useCardTextures } from "../three/CardTextures";
import { CardMesh } from "../three/CardMesh";

/** The 12 cards fanned into a leaned-back cascade with a slow idle drift. */
function HeroDeck() {
  const textures = useCardTextures();
  const group = useRef<THREE.Group>(null!);

  useFrame((state) => {
    const t = state.clock.elapsedTime;
    group.current.rotation.y = 0.5 + Math.sin(t * 0.28) * 0.16;
    group.current.rotation.x = -0.46 + Math.sin(t * 0.34) * 0.05;
    group.current.position.y = Math.sin(t * 0.5) * 0.06;
  });

  return (
    <group ref={group} position={[1.5, 0.1, 0]}>
      {CARDS.map((card, i) => {
        const ct = textures.get(card.id)!;
        const offset = i - (CARDS.length - 1) / 2;
        return (
          <group
            key={card.id}
            position={[offset * 0.045, offset * 0.12, offset * 0.16]}
            rotation={[0, 0, offset * 0.01]}
          >
            <CardMesh
              card={card}
              texture={ct.texture}
              hotspots={ct.hotspots}
            />
          </group>
        );
      })}
    </group>
  );
}

export function HeroPage() {
  return (
    <section className="relative h-screen w-full overflow-hidden">
      <Canvas
        style={{ position: "absolute", inset: 0 }}
        camera={{ position: [0, 0, 7], fov: 38 }}
        gl={{ antialias: true, alpha: true }}
        dpr={[1, 2]}
      >
        <ambientLight intensity={1.1} />
        <directionalLight position={[3, 5, 4]} intensity={2.2} />
        <directionalLight
          position={[-4, -2, 2]}
          intensity={0.6}
          color="#4d8dff"
        />
        <HeroDeck />
      </Canvas>

      {/* copy — left column, vertically centred */}
      <div className="pointer-events-none relative z-10 flex h-full items-center">
        <div className="fm-rise max-w-[560px] px-10 sm:px-16">
          <div className="font-mono text-[12px] uppercase tracking-[0.34em] text-[var(--color-accent)]">
            Ambient Intelligence
          </div>
          <h1 className="mt-5 text-[88px] font-semibold leading-[0.95] tracking-[-0.03em] text-[var(--color-text-primary)]">
            AgentV
          </h1>
          <h2 className="mt-4 font-mono text-[16px] uppercase leading-snug tracking-[0.16em] text-[var(--color-text-secondary)]">
            Glanceable ambient intelligence for XR
          </h2>
          <p className="mt-7 text-[15px] leading-relaxed text-[var(--color-text-secondary)]">
            AgentV is a workplace-ready ambient assistant that stays quiet
            until you clearly address it — not on every conversation in the
            room. It listens for intent, acts only when appropriate, and uses
            on-device AI to handle everyday tasks: timers, calendar, search,
            drafts, and navigation. Results appear as short, confirmable
            prompts — not a chat thread.
          </p>
          <p className="mt-5 border-l border-[var(--color-stroke)] pl-4 text-[14px] leading-relaxed text-[var(--color-text-faint)]">
            We're building on a working Pixel prototype today and targeting
            Android XR as the primary glanceable surface for this flow.
          </p>
          <div className="mt-9 font-mono text-[10px] uppercase tracking-[0.24em] text-[var(--color-text-faint)]">
            Scroll — design doc ↓
          </div>
        </div>
      </div>
    </section>
  );
}
