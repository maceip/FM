/**
 * The Pixel 10 Fold GLB model.
 *
 * Drop the supplied file at public/models/pixel-fold.glb. The model is
 * expected to carry a fold/unfold animation clip; this component parks every
 * clip paused and scrubs it by `openRef` (0 = folded shut, 1 = fully open)
 * so the unfold is driven by scroll rather than playing on its own.
 *
 * If the GLB has no clips the component still renders the static mesh — the
 * intro's spin, bounce and settle still work; only the unfold needs the clip.
 */
import { useEffect, useRef, type MutableRefObject } from "react";
import { useGLTF, useAnimations } from "@react-three/drei";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

export const PHONE_MODEL_URL = "/models/pixel-fold.glb";

interface PhoneModelProps {
  /** 0 = folded shut, 1 = fully open. */
  openRef: MutableRefObject<number>;
}

export function PhoneModel({ openRef }: PhoneModelProps) {
  const gltf = useGLTF(PHONE_MODEL_URL);
  const group = useRef<THREE.Group>(null!);
  const { actions, mixer } = useAnimations(gltf.animations, group);

  useEffect(() => {
    // Park each clip paused so it can be scrubbed instead of playing.
    Object.values(actions).forEach((action) => {
      if (!action) return;
      action.play();
      action.paused = true;
    });
  }, [actions]);

  useFrame(() => {
    const open = THREE.MathUtils.clamp(openRef.current, 0, 1);
    let scrubbed = false;
    Object.values(actions).forEach((action) => {
      if (!action) return;
      action.time = action.getClip().duration * open;
      scrubbed = true;
    });
    if (scrubbed) mixer.update(0);
  });

  return <primitive ref={group} object={gltf.scene} />;
}
