/**
 * The "orb" core component as a 3D object.
 *
 * An orb is a circular HUD gauge: two concentric rings of small radial
 * "chits", a fine tick ring, four cardinal diamonds and a glowing centre
 * icon. The orb has no real interactivity — its whole behaviour is colour:
 * every chit is an individually addressable instance, so a parent just
 * hands in an array of colours per ring.
 *
 *  - OrbGauge  — just the gauge, mounted on the z=0 plane of its group, so
 *                it can be dropped onto any tile (the orb tile, the tab).
 *  - OrbMesh   — OrbGauge on its own rounded square tile.
 *
 * Built from instanced meshes (one draw call per ring) so colouring a chit
 * is a cheap `setColorAt`.
 */
import {
  forwardRef,
  useLayoutEffect,
  useMemo,
  useRef,
  type ComponentProps,
} from "react";
import { RoundedBox } from "@react-three/drei";
import * as THREE from "three";

export const ORB_OUTER_COUNT = 48;
export const ORB_INNER_COUNT = 40;

export type OrbIcon = "dot" | "square" | "alert";

export interface OrbState {
  /** One colour per outer-ring chit (length ORB_OUTER_COUNT). */
  outerColors: THREE.ColorRepresentation[];
  /** One colour per inner-ring chit (length ORB_INNER_COUNT). */
  innerColors: THREE.ColorRepresentation[];
  /** Colour of the centre icon + its glow. */
  centerColor: THREE.ColorRepresentation;
  icon: OrbIcon;
}

/* Orb-local units. The gauge's natural radius (to the diamonds) is ~1.1. */
const TILE = 2.3;
const TILE_DEPTH = 0.16;
const R_OUTER = 0.78;
const R_INNER = 0.55;
const R_TICK = 0.95;
const TICK_COUNT = 110;

/** A ring of radially-oriented chit instances, each individually coloured. */
function ChitRing({
  count,
  radius,
  size,
  colors,
}: {
  count: number;
  radius: number;
  size: [number, number, number];
  colors?: THREE.ColorRepresentation[];
}) {
  const ref = useRef<THREE.InstancedMesh>(null!);

  useLayoutEffect(() => {
    const mesh = ref.current;
    const dummy = new THREE.Object3D();
    for (let i = 0; i < count; i++) {
      const a = (i / count) * Math.PI * 2 - Math.PI / 2;
      dummy.position.set(Math.cos(a) * radius, Math.sin(a) * radius, 0);
      // box +Y is its length — rotate so it points radially outward
      dummy.rotation.set(0, 0, a - Math.PI / 2);
      dummy.updateMatrix();
      mesh.setMatrixAt(i, dummy.matrix);
    }
    mesh.instanceMatrix.needsUpdate = true;
  }, [count, radius]);

  useLayoutEffect(() => {
    const mesh = ref.current;
    const c = new THREE.Color();
    for (let i = 0; i < count; i++) {
      mesh.setColorAt(i, c.set(colors?.[i] ?? "#1b2436"));
    }
    if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
  }, [colors, count]);

  return (
    <instancedMesh
      ref={ref}
      args={[undefined, undefined, count]}
      position={[0, 0, 0.02]}
    >
      <boxGeometry args={size} />
      <meshBasicMaterial toneMapped={false} />
    </instancedMesh>
  );
}

function CenterIcon({
  icon,
  color,
}: {
  icon: OrbIcon;
  color: THREE.ColorRepresentation;
}) {
  const squareGeometry = useMemo(() => {
    const o = 0.12;
    const i = 0.075;
    const shape = new THREE.Shape();
    shape.moveTo(-o, -o);
    shape.lineTo(o, -o);
    shape.lineTo(o, o);
    shape.lineTo(-o, o);
    shape.closePath();
    const hole = new THREE.Path();
    hole.moveTo(-i, -i);
    hole.lineTo(i, -i);
    hole.lineTo(i, i);
    hole.lineTo(-i, i);
    hole.closePath();
    shape.holes.push(hole);
    return new THREE.ShapeGeometry(shape);
  }, []);

  return (
    <group position={[0, 0, 0.04]}>
      {icon === "dot" && (
        <mesh>
          <circleGeometry args={[0.085, 28]} />
          <meshBasicMaterial color={color} toneMapped={false} />
        </mesh>
      )}
      {icon === "square" && (
        <mesh geometry={squareGeometry}>
          <meshBasicMaterial color={color} toneMapped={false} />
        </mesh>
      )}
      {icon === "alert" && (
        <>
          <mesh position={[0, 0.055, 0]}>
            <boxGeometry args={[0.052, 0.16, 0.03]} />
            <meshBasicMaterial color={color} toneMapped={false} />
          </mesh>
          <mesh position={[0, -0.09, 0]}>
            <boxGeometry args={[0.052, 0.052, 0.03]} />
            <meshBasicMaterial color={color} toneMapped={false} />
          </mesh>
        </>
      )}
    </group>
  );
}

/** Soft additive radial glow — generated once, tinted per orb. */
function useGlowTexture() {
  return useMemo(() => {
    const c = document.createElement("canvas");
    c.width = c.height = 128;
    const ctx = c.getContext("2d")!;
    const g = ctx.createRadialGradient(64, 64, 0, 64, 64, 64);
    g.addColorStop(0, "rgba(255,255,255,1)");
    g.addColorStop(0.45, "rgba(255,255,255,0.35)");
    g.addColorStop(1, "rgba(255,255,255,0)");
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, 128, 128);
    const tex = new THREE.CanvasTexture(c);
    tex.colorSpace = THREE.SRGBColorSpace;
    return tex;
  }, []);
}

type GroupProps = ComponentProps<"group">;

/** Just the gauge, drawn on the z=0 plane of its group. */
export function OrbGauge({
  state,
  ...groupProps
}: GroupProps & { state: OrbState }) {
  const glow = useGlowTexture();
  const ticks = useMemo(() => new Array(TICK_COUNT).fill("#33405c"), []);
  const diamonds = useMemo(
    () => [0, 1, 2, 3].map((q) => (q * Math.PI) / 2 - Math.PI / 2),
    [],
  );

  return (
    <group {...groupProps}>
      <ChitRing
        count={TICK_COUNT}
        radius={R_TICK}
        size={[0.015, 0.05, 0.03]}
        colors={ticks}
      />
      {diamonds.map((a, i) => (
        <mesh
          key={i}
          position={[
            Math.cos(a) * (R_TICK + 0.12),
            Math.sin(a) * (R_TICK + 0.12),
            0.02,
          ]}
          rotation={[0, 0, Math.PI / 4]}
        >
          <boxGeometry args={[0.07, 0.07, 0.035]} />
          <meshBasicMaterial color="#5a6b8c" toneMapped={false} />
        </mesh>
      ))}
      <ChitRing
        count={ORB_OUTER_COUNT}
        radius={R_OUTER}
        size={[0.055, 0.17, 0.045]}
        colors={state.outerColors}
      />
      <ChitRing
        count={ORB_INNER_COUNT}
        radius={R_INNER}
        size={[0.05, 0.115, 0.045]}
        colors={state.innerColors}
      />
      <mesh position={[0, 0, 0.01]}>
        <circleGeometry args={[0.3, 40]} />
        <meshStandardMaterial color="#080c15" roughness={0.7} />
      </mesh>
      <mesh position={[0, 0, 0.02]}>
        <planeGeometry args={[0.9, 0.9]} />
        <meshBasicMaterial
          map={glow}
          color={state.centerColor}
          transparent
          blending={THREE.AdditiveBlending}
          depthWrite={false}
          opacity={0.55}
          toneMapped={false}
        />
      </mesh>
      <CenterIcon icon={state.icon} color={state.centerColor} />
    </group>
  );
}

interface OrbMeshProps extends GroupProps {
  state: OrbState;
}

/** The orb on its own rounded square tile. */
export const OrbMesh = forwardRef<THREE.Group, OrbMeshProps>(
  function OrbMesh({ state, ...groupProps }, ref) {
    return (
      <group ref={ref} {...groupProps}>
        <RoundedBox args={[TILE, TILE, TILE_DEPTH]} radius={0.42} smoothness={4}>
          <meshStandardMaterial
            color="#0b111d"
            roughness={0.58}
            metalness={0.16}
          />
        </RoundedBox>
        <OrbGauge state={state} position={[0, 0, TILE_DEPTH / 2]} />
      </group>
    );
  },
);
