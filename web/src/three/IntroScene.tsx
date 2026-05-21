/**
 * Scroll-driven intro: the Pixel 10 Fold model performs, then hands off to
 * the flat outline.
 *
 * Page load           — dune background + the folded phone model at rest.
 * scroll 0   → 0.30   — model bounces gently and turns one full
 *                       counterclockwise revolution.
 * scroll 0.30 → 0.62  — the fold slowly opens (the GLB clip is scrubbed).
 * scroll 0.62 → 0.86  — the open model eases to the outline's place + size.
 * scroll 0.86 → 1.00  — the model fades out, leaving the flat screen outline.
 *
 * The model file isn't here yet — until public/models/pixel-fold.glb exists
 * ModelBoundary swallows the 404 and the intro runs with just the outline.
 * The tuning constants below (scale/offset) are placeholders to set against
 * the real model once it lands.
 */
import { Suspense, useEffect, useRef, useState } from "react";
import { Canvas, useFrame } from "@react-three/fiber";
import { ScrollControls, useScroll } from "@react-three/drei";
import * as THREE from "three";
import { Background } from "../Background";
import { PhoneFrame } from "./PhoneFrame";
import { PhoneModel, PHONE_MODEL_URL } from "./PhoneModel";
import { ModelBoundary } from "./ModelBoundary";

/**
 * True once the GLB is actually present. The dev/static server answers a
 * missing file with the SPA's index.html, so a 200 isn't enough — the
 * content-type must not be HTML.
 */
function useModelReady(url: string) {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    let live = true;
    fetch(url)
      .then((r) => {
        const isHtml = r.headers
          .get("content-type")
          ?.includes("text/html");
        if (live) setReady(r.ok && !isHtml);
      })
      .catch(() => live && setReady(false));
    return () => {
      live = false;
    };
  }, [url]);
  return ready;
}

/* timeline boundaries, in scroll-offset units */
const SPIN_END = 0.3;
const OPEN_END = 0.62;
const SETTLE_END = 0.86;

/* placeholders — calibrate to the real GLB once supplied */
const MODEL_SCALE = 1.4;
const OUTLINE_SCALE = 1;

const clamp01 = (v: number) => THREE.MathUtils.clamp(v, 0, 1);
const easeInOut = (t: number) =>
  t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;

/** Fades every mesh material under a node. */
function setOpacity(node: THREE.Object3D, opacity: number) {
  node.traverse((o) => {
    const mat = (o as THREE.Mesh).material as THREE.Material | undefined;
    if (mat && "opacity" in mat) {
      mat.transparent = true;
      mat.opacity = opacity;
    }
  });
}

function IntroSequence() {
  const scroll = useScroll();
  const open = useRef(0);
  const model = useRef<THREE.Group>(null!);
  const outline = useRef<THREE.Group>(null!);
  const modelReady = useModelReady(PHONE_MODEL_URL);

  useFrame((state) => {
    const t = scroll.offset;
    const time = state.clock.elapsedTime;

    const spin = clamp01(t / SPIN_END);
    open.current = clamp01((t - SPIN_END) / (OPEN_END - SPIN_END));
    const settle = clamp01((t - OPEN_END) / (SETTLE_END - OPEN_END));
    const vanish = clamp01((t - SETTLE_END) / (1 - SETTLE_END));

    if (model.current) {
      const g = model.current;
      // one full counterclockwise turn (negative Y, seen from the front)
      g.rotation.y = -Math.PI * 2 * easeInOut(spin);
      // gentle bounce, strongest before the fold opens
      const bounce =
        Math.sin(time * 3.4) * 0.1 * (1 - clamp01(t / OPEN_END));
      g.position.set(0, bounce, 0);
      g.scale.setScalar(
        THREE.MathUtils.lerp(MODEL_SCALE, OUTLINE_SCALE, easeInOut(settle)),
      );
      const opacity = 1 - vanish;
      g.visible = opacity > 0.001;
      if (g.visible) setOpacity(g, opacity);
    }

    if (outline.current) {
      const o = clamp01(settle * 1.15);
      outline.current.visible = o > 0.001;
      if (outline.current.visible) setOpacity(outline.current, o);
    }
  });

  return (
    <group>
      {/* GLB model — mounted only once the .glb is actually present */}
      <group ref={model}>
        {modelReady && (
          <ModelBoundary fallback={null}>
            <Suspense fallback={null}>
              <PhoneModel openRef={open} />
            </Suspense>
          </ModelBoundary>
        )}
      </group>

      {/* the flat outline the model resolves into */}
      <PhoneFrame ref={outline} visible={false} />
    </group>
  );
}

export function IntroScene() {
  return (
    <>
      <Background />
      <Canvas
        className="fm-rise"
        camera={{ position: [0, 0, 8], fov: 42 }}
        gl={{ antialias: true, alpha: true }}
        dpr={[1, 2]}
        style={{ position: "relative" }}
      >
        <ambientLight intensity={1} />
        <directionalLight position={[3, 5, 5]} intensity={2.2} />
        <directionalLight
          position={[-4, -1, 3]}
          intensity={0.7}
          color="#5b8fff"
        />
        <ScrollControls pages={3} damping={0.26}>
          <IntroSequence />
        </ScrollControls>
      </Canvas>
    </>
  );
}
