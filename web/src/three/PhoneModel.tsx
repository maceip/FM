/**
 * The Pixel 10 Fold GLB model.
 *
 * The model carries two hinge clips (hinge_LAction / hinge_RAction) that
 * swing the left/right wings open. Every clip is parked paused and scrubbed
 * by `openRef` (0 = folded shut, 1 = fully open) so the unfold is driven by
 * scroll rather than playing on its own.
 *
 * The mesh is authored lying flat (screen up, +Y); a base X rotation stands
 * it upright so the screen faces the camera.
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
      // the clips run open (t=0) → folded (t=duration), so invert
      action.time = action.getClip().duration * (1 - open);
      scrubbed = true;
    });
    if (scrubbed) mixer.update(0);
  });

  // authored lying flat — stand it upright with the inner display facing
  // the camera (the cameras/cover screen are on the far face)
  return (
    <primitive
      ref={group}
      object={gltf.scene}
      rotation={[-Math.PI / 2, 0, 0]}
    />
  );
}
