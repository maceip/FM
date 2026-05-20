/**
 * A single FM card as a real 3D object.
 *
 * It is a group of two parts:
 *  - an extruded rounded-rectangle BODY that gives the card genuine
 *    thickness and perpendicular side walls (so a leaned-back deck reads as
 *    a stack of slabs, not a stack of paper);
 *  - a textured FRONT plane sitting just proud of the body, carrying the
 *    card's rasterised UI.
 *
 * Extruding the same rounded-rect shape keeps the corner radius independent
 * of the thickness, so the body's edges line up exactly with the texture's
 * transparent rounded corners at any depth.
 *
 * The group ref is forwarded so a parent scene can perturb position,
 * rotation and scale — the cards are meant to be moved around in 3D.
 */
import { forwardRef, useMemo } from "react";
import * as THREE from "three";
import type { CardDef } from "../cards/registry";

/** Pixels of card height that map to one world unit (the grid-card height). */
const UNIT = 412;
/** Card thickness in world units (~5% of card height). */
const THICKNESS = 0.05;
/** Corner radius in world units (matches the 20px CSS border-radius). */
const RADIUS = 20 / UNIT;

function roundedRectShape(w: number, h: number, r: number) {
  const s = new THREE.Shape();
  const x = -w / 2;
  const y = -h / 2;
  s.moveTo(x + r, y);
  s.lineTo(x + w - r, y);
  s.quadraticCurveTo(x + w, y, x + w, y + r);
  s.lineTo(x + w, y + h - r);
  s.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
  s.lineTo(x + r, y + h);
  s.quadraticCurveTo(x, y + h, x, y + h - r);
  s.lineTo(x, y + r);
  s.quadraticCurveTo(x, y, x + r, y);
  return s;
}

interface CardMeshProps {
  card: CardDef;
  texture: THREE.Texture;
}

export const CardMesh = forwardRef<THREE.Group, CardMeshProps>(
  function CardMesh({ card, texture }, ref) {
    const w = card.width / UNIT;
    const h = card.height / UNIT;

    const bodyGeometry = useMemo(() => {
      const geo = new THREE.ExtrudeGeometry(roundedRectShape(w, h, RADIUS), {
        depth: THICKNESS,
        bevelEnabled: false,
        curveSegments: 8,
      });
      // Extrusion runs 0→depth on Z; recentre it on the origin.
      geo.translate(0, 0, -THICKNESS / 2);
      return geo;
    }, [w, h]);

    return (
      <group ref={ref}>
        {/* card body — gives the slab its thickness */}
        <mesh geometry={bodyGeometry}>
          <meshStandardMaterial
            color="#0e1626"
            roughness={0.62}
            metalness={0.12}
          />
        </mesh>
        {/* card face — the rasterised UI, parked just proud of the body */}
        <mesh position={[0, 0, THICKNESS / 2 + 0.003]}>
          <planeGeometry args={[w, h]} />
          <meshBasicMaterial
            map={texture}
            transparent
            alphaTest={0.35}
            side={THREE.DoubleSide}
            toneMapped={false}
          />
        </mesh>
      </group>
    );
  },
);
