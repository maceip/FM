/**
 * Pixel 10 Fold (expanded) — just the outline.
 *
 * A black stroke in the shape of the unfolded fold: a near-square rounded
 * rectangle. The components anchor inside it. PHONE exposes the rect so a
 * layout can pin cards/tab to the right edge and the orb to the bottom.
 */
import { forwardRef, useMemo, type ComponentProps } from "react";
import { Line } from "@react-three/drei";
import * as THREE from "three";

export const PHONE = {
  /** Unfolded inner display — near-square, slightly portrait. */
  width: 3.74,
  height: 3.94,
  cornerRadius: 0.34,
};

/** Points tracing a closed rounded-rectangle path. */
function roundedRectPoints(
  w: number,
  h: number,
  r: number,
  segs = 12,
): THREE.Vector3[] {
  const hw = w / 2;
  const hh = h / 2;
  const corners = [
    { cx: hw - r, cy: hh - r, a0: 0 }, // top-right
    { cx: -hw + r, cy: hh - r, a0: Math.PI / 2 }, // top-left
    { cx: -hw + r, cy: -hh + r, a0: Math.PI }, // bottom-left
    { cx: hw - r, cy: -hh + r, a0: (3 * Math.PI) / 2 }, // bottom-right
  ];
  const pts: THREE.Vector3[] = [];
  for (const c of corners) {
    for (let i = 0; i <= segs; i++) {
      const a = c.a0 + (i / segs) * (Math.PI / 2);
      pts.push(
        new THREE.Vector3(c.cx + Math.cos(a) * r, c.cy + Math.sin(a) * r, 0),
      );
    }
  }
  pts.push(pts[0].clone()); // close the loop
  return pts;
}

export const PhoneFrame = forwardRef<THREE.Group, ComponentProps<"group">>(
  function PhoneFrame(props, ref) {
    const points = useMemo(
      () =>
        roundedRectPoints(PHONE.width, PHONE.height, PHONE.cornerRadius),
      [],
    );
    return (
      <group ref={ref} {...props}>
        <Line points={points} color="#000000" lineWidth={2} />
      </group>
    );
  },
);
