/**
 * Pixel 10 Fold (expanded) — the device frame the components live inside.
 *
 * Built as a 3D object so the card deck, the orb and the tab can be anchored
 * to it and posed together: a metal rail (rounded extruded body), a recessed
 * glass screen, the foldable's centre crease, a punch-hole camera and the
 * side buttons. Expanded fold = the near-square inner display.
 *
 * PHONE exposes the geometry so a layout can anchor children to the screen —
 * e.g. cards/tab to the right rail, orb to the bottom centre.
 */
import { forwardRef, useMemo, type ComponentProps } from "react";
import { RoundedBox } from "@react-three/drei";
import * as THREE from "three";

/** Dark screen wallpaper — a deep gradient with a faint centre glow, so the
 *  empty screen reads as "on" rather than as a void. */
function makeScreenTexture(): THREE.Texture {
  const c = document.createElement("canvas");
  c.width = 512;
  c.height = 540;
  const ctx = c.getContext("2d")!;
  let g: CanvasGradient = ctx.createLinearGradient(0, 0, 0, 540);
  g.addColorStop(0, "#0e1626");
  g.addColorStop(1, "#05070e");
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, 512, 540);
  g = ctx.createRadialGradient(256, 256, 0, 256, 256, 360);
  g.addColorStop(0, "rgba(64,104,184,0.22)");
  g.addColorStop(1, "rgba(0,0,0,0)");
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, 512, 540);
  const tex = new THREE.CanvasTexture(c);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

export const PHONE = {
  bodyW: 3.74,
  bodyH: 3.94,
  bodyD: 0.22,
  cornerRadius: 0.3,
  /** Inner display — near-square, slightly portrait (fold expanded). */
  screenW: 3.4,
  screenH: 3.58,
  /** World-space z of the screen surface (anchor children just in front). */
  screenZ: 0.22 / 2 - 0.005,
};

const SURFACE = PHONE.screenZ + 0.002;

export const PhoneFrame = forwardRef<THREE.Group, ComponentProps<"group">>(
  function PhoneFrame(props, ref) {
    const screenTexture = useMemo(makeScreenTexture, []);
    return (
      <group ref={ref} {...props}>
        {/* body + rail */}
        <RoundedBox
          args={[PHONE.bodyW, PHONE.bodyH, PHONE.bodyD]}
          radius={PHONE.cornerRadius}
          smoothness={6}
        >
          <meshStandardMaterial
            color="#2b2f3a"
            metalness={0.78}
            roughness={0.32}
          />
        </RoundedBox>

        {/* recessed glass screen — the body front shows as the bezel */}
        <RoundedBox
          args={[PHONE.screenW, PHONE.screenH, 0.05]}
          radius={0.2}
          smoothness={5}
          position={[0, 0, PHONE.bodyD / 2 - 0.03]}
        >
          <meshStandardMaterial
            color="#070a12"
            metalness={0.45}
            roughness={0.1}
          />
        </RoundedBox>

        {/* screen wallpaper — square-cornered but inset enough to stay
            inside the glass's rounded corners */}
        <mesh position={[0, 0, SURFACE]}>
          <planeGeometry args={[PHONE.screenW - 0.24, PHONE.screenH - 0.24]} />
          <meshBasicMaterial map={screenTexture} toneMapped={false} />
        </mesh>

        {/* foldable centre crease — a faint vertical reflection line */}
        <mesh position={[0, 0, SURFACE + 0.001]}>
          <planeGeometry args={[0.055, PHONE.screenH - 0.26]} />
          <meshBasicMaterial
            color="#8fa6c8"
            transparent
            opacity={0.1}
            toneMapped={false}
          />
        </mesh>

        {/* punch-hole camera */}
        <mesh position={[0, PHONE.screenH / 2 - 0.28, SURFACE + 0.003]}>
          <circleGeometry args={[0.05, 28]} />
          <meshBasicMaterial color="#01020a" toneMapped={false} />
        </mesh>
        <mesh position={[0, PHONE.screenH / 2 - 0.28, SURFACE + 0.002]}>
          <ringGeometry args={[0.05, 0.064, 28]} />
          <meshBasicMaterial color="#2b3650" toneMapped={false} />
        </mesh>

        {/* side buttons on the right rail */}
        <mesh position={[PHONE.bodyW / 2, 0.58, 0]}>
          <boxGeometry args={[0.06, 0.62, 0.12]} />
          <meshStandardMaterial
            color="#2a2e38"
            metalness={0.8}
            roughness={0.42}
          />
        </mesh>
        <mesh position={[PHONE.bodyW / 2, -0.16, 0]}>
          <boxGeometry args={[0.06, 0.34, 0.11]} />
          <meshStandardMaterial
            color="#2a2e38"
            metalness={0.8}
            roughness={0.42}
          />
        </mesh>
      </group>
    );
  },
);
