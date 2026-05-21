/**
 * Scroll-driven scene for the FM marketing site.
 *
 * State A (scroll = 0): all 12 cards collapsed into a single deck, each leaned
 *   far back on the X axis so the stack reads as a thin slab.
 * State B (scroll = 1): the focal card (the Mini Browser, parked mid-deck)
 *   flips forward to face the camera and scales up, while every card above it
 *   is pushed up and fanned out of the way.
 *
 * Poses are computed directly from the eased scroll offset so the motion is
 * deterministic and reverses cleanly when the user scrolls back up.
 */
import { useMemo, useRef } from "react";
import { useFrame } from "@react-three/fiber";
import { useScroll } from "@react-three/drei";
import * as THREE from "three";
import { CARDS, type CardDef } from "../cards/registry";
import type { CardTexture } from "./CardTextures";
import { CardMesh } from "./CardMesh";

/** Where the focal card sits in the deck (cards above it get pushed away). */
const FOCUS_INDEX = 6;

function easeInOut(t: number) {
  return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
}

export function StackScene({
  textures,
}: {
  textures: Map<string, CardTexture>;
}) {
  const group = useRef<THREE.Group>(null!);
  const cards = useRef<(THREE.Group | null)[]>([]);
  const scroll = useScroll();

  // Deck order: pull the Mini Browser into the middle so real cards sit
  // both above and around it — the ones the flip pushes aside.
  const deck = useMemo<CardDef[]>(() => {
    const d = [...CARDS];
    const bi = d.findIndex((c) => c.id === "browser");
    const [browser] = d.splice(bi, 1);
    d.splice(FOCUS_INDEX, 0, browser);
    return d;
  }, []);

  useFrame((state) => {
    const t = easeInOut(THREE.MathUtils.clamp(scroll.offset, 0, 1));
    const time = state.clock.elapsedTime;
    const n = deck.length;

    deck.forEach((_, i) => {
      const card = cards.current[i];
      if (!card) return;
      const offset = i - n / 2;

      // --- State A: leaned-back deck --------------------------------------
      let px = 0;
      let py = offset * 0.14;
      let pz = offset * 0.17;
      let rx = -1.12;
      let ry = 0;
      const rz = 0;
      let scale = 1;
      let opacity = 1;

      if (i === FOCUS_INDEX) {
        // --- State B: flip the focal card forward -------------------------
        px = THREE.MathUtils.lerp(px, 0, t);
        py = THREE.MathUtils.lerp(py, 0.5, t);
        pz = THREE.MathUtils.lerp(pz, 2.0, t);
        rx = THREE.MathUtils.lerp(rx, 0, t);
        scale = THREE.MathUtils.lerp(1, 1.7, t);
      } else if (i > FOCUS_INDEX) {
        // Cards above the focal card: shoved up and fanned out.
        const k = i - FOCUS_INDEX;
        const dir = k % 2 === 0 ? 1 : -1;
        px += t * dir * (0.8 + k * 0.22);
        py += t * (1.0 + k * 0.5);
        pz += t * k * 0.2;
        opacity = 1 - t * 0.92;
      } else {
        // Cards below: settle back and dim so the focal card pops.
        pz -= t * 0.3;
        opacity = 1 - t * 0.45;
      }

      // Subtle idle life while the deck is at rest.
      const idle = (1 - t) * 0.04;
      ry += Math.sin(time * 0.6 + i) * idle;

      card.position.set(px, py, pz);
      card.rotation.set(rx, ry, rz);
      card.scale.setScalar(scale);

      // Fade the whole card — body and face — when it's pushed aside.
      card.traverse((o) => {
        const mat = (o as THREE.Mesh).material as THREE.Material | undefined;
        if (mat && "opacity" in mat) {
          mat.transparent = true;
          mat.opacity = opacity;
        }
      });
    });

    // Whole-deck breathing rotation, only while stacked.
    group.current.rotation.y = Math.sin(time * 0.25) * 0.12 * (1 - t);
  });

  return (
    <group ref={group} position={[0, 0.1, 0]}>
      {deck.map((card, i) => {
        const ct = textures.get(card.id)!;
        return (
          <CardMesh
            key={card.id}
            ref={(m) => {
              cards.current[i] = m;
            }}
            card={card}
            texture={ct.texture}
            hotspots={ct.hotspots}
          />
        );
      })}
    </group>
  );
}
