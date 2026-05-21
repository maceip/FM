/**
 * The "tab" core component as a 3D object.
 *
 * A tab is an orb gauge paired with a status read-out, in a wide pill tile.
 * Like the cards it anchors to the phone's right rail. It has no real
 * interactivity — the orb shows state through colour, the panel shows it
 * as text.
 *
 * The status text is drawn straight to a canvas (system monospace, no font
 * fetch) and mapped onto a plane, which keeps the tab self-contained.
 */
import { forwardRef, useMemo, type ComponentProps } from "react";
import { RoundedBox } from "@react-three/drei";
import * as THREE from "three";
import { OrbGauge, type OrbState } from "./OrbMesh";

export interface TabLine {
  label: string;
  value: string;
}

export interface TabState {
  orb: OrbState;
  title: string;
  lines: TabLine[];
}

const TAB_W = 5.2;
const TAB_H = 2.3;
const TAB_DEPTH = 0.16;
const FACE_Z = TAB_DEPTH / 2;

const ORB_X = -TAB_W / 2 + 1.2;
const DIVIDER_X = -0.35;
const TEXT_CENTER_X = 1.18;
const TEXT_W = 2.78;
const TEXT_H = 1.86;

/** Draws the tab's title + status lines to a CanvasTexture. */
function makeTextTexture(title: string, lines: TabLine[]): THREE.Texture {
  const W = 1100;
  const H = Math.round(W * (TEXT_H / TEXT_W));
  const canvas = document.createElement("canvas");
  canvas.width = W;
  canvas.height = H;
  const ctx = canvas.getContext("2d")!;
  ctx.textBaseline = "alphabetic";

  // title
  ctx.font = '600 96px "JetBrains Mono", ui-monospace, monospace';
  ctx.letterSpacing = "7px";
  ctx.fillStyle = "#eef2f8";
  ctx.fillText(title.toUpperCase(), 16, 118);

  // divider dash under the title
  ctx.fillStyle = "#4d8dff";
  ctx.fillRect(18, 158, 56, 5);

  // status lines: label left, value in a fixed column
  ctx.font = '500 52px "JetBrains Mono", ui-monospace, monospace';
  ctx.letterSpacing = "4px";
  const top = 268;
  const step = (H - top - 40) / Math.max(lines.length, 1);
  lines.forEach((line, i) => {
    const y = top + step * i;
    ctx.fillStyle = "#9aa3b6";
    ctx.fillText(line.label.toUpperCase(), 16, y);
    ctx.fillStyle = "#e7ecf6";
    ctx.fillText(line.value.toUpperCase(), 560, y);
  });

  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 8;
  return tex;
}

type GroupProps = ComponentProps<"group">;

interface TabMeshProps extends GroupProps {
  state: TabState;
}

export const TabMesh = forwardRef<THREE.Group, TabMeshProps>(
  function TabMesh({ state, ...groupProps }, ref) {
    const textTexture = useMemo(
      () => makeTextTexture(state.title, state.lines),
      [state.title, state.lines],
    );

    return (
      <group ref={ref} {...groupProps}>
        {/* pill tile */}
        <RoundedBox
          args={[TAB_W, TAB_H, TAB_DEPTH]}
          radius={TAB_H / 2}
          smoothness={5}
        >
          <meshStandardMaterial
            color="#0b111d"
            roughness={0.58}
            metalness={0.16}
          />
        </RoundedBox>

        {/* orb gauge on the left */}
        <OrbGauge
          state={state.orb}
          position={[ORB_X, 0, FACE_Z]}
          scale={0.8}
        />

        {/* vertical divider */}
        <mesh position={[DIVIDER_X, 0, FACE_Z + 0.01]}>
          <boxGeometry args={[0.022, 1.66, 0.03]} />
          <meshBasicMaterial color="#2f3c58" toneMapped={false} />
        </mesh>

        {/* status read-out */}
        <mesh position={[TEXT_CENTER_X, 0, FACE_Z + 0.01]}>
          <planeGeometry args={[TEXT_W, TEXT_H]} />
          <meshBasicMaterial map={textTexture} transparent toneMapped={false} />
        </mesh>
      </group>
    );
  },
);
