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
 * The card's buttons are baked into the texture, so they can't receive DOM
 * events. Instead each interactive element's bounding box is captured as a
 * UV-space hotspot; the face mesh raycasts the pointer against them and the
 * face material lights the hovered region with a soft blue glow.
 *
 * The group ref is forwarded so a parent scene can perturb position,
 * rotation and scale — the cards are meant to be moved around in 3D.
 */
import { forwardRef, useMemo, useRef } from "react";
import { useFrame, type ThreeEvent } from "@react-three/fiber";
import * as THREE from "three";
import type { CardDef } from "../cards/registry";
import type { Hotspot } from "./CardTextures";

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
  hotspots: Hotspot[];
}

export const CardMesh = forwardRef<THREE.Group, CardMeshProps>(
  function CardMesh({ card, texture, hotspots }, ref) {
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

    // Glow state: animated intensity + the UV rect of the hovered hotspot.
    const glow = useRef({
      value: 0,
      target: 0,
      rect: new THREE.Vector4(0, 0, 0, 0),
    });
    // Shader uniforms, captured once the material compiles.
    const uniforms = useRef<{
      uRect: { value: THREE.Vector4 };
      uGlow: { value: number };
    } | null>(null);

    // Face material: MeshBasicMaterial patched so the hovered hotspot's UV
    // rect brightens and emits a soft blue halo. Patching the stock material
    // (rather than a raw ShaderMaterial) keeps opacity/alphaTest working so
    // the scene's fade-out still applies.
    const faceMaterial = useMemo(() => {
      const m = new THREE.MeshBasicMaterial({
        map: texture,
        transparent: true,
        alphaTest: 0.35,
        side: THREE.DoubleSide,
        toneMapped: false,
      });
      m.onBeforeCompile = (shader) => {
        shader.uniforms.uRect = { value: new THREE.Vector4(0, 0, 0, 0) };
        shader.uniforms.uGlow = { value: 0 };
        uniforms.current = {
          uRect: shader.uniforms.uRect,
          uGlow: shader.uniforms.uGlow,
        };
        shader.fragmentShader = shader.fragmentShader
          .replace(
            "#include <common>",
            `#include <common>
             uniform vec4 uRect;
             uniform float uGlow;`,
          )
          .replace(
            "#include <map_fragment>",
            `#include <map_fragment>
             if (uGlow > 0.001 && uRect.z > 0.0) {
               vec2 hc = uRect.xy + uRect.zw * 0.5;
               vec2 hd = abs(vMapUv - hc) - uRect.zw * 0.5;
               float hsd = length(max(hd, 0.0)) + min(max(hd.x, hd.y), 0.0);
               // soft fill, plus a brighter band hugging the button edge
               float fill = 1.0 - smoothstep(-0.03, 0.01, hsd);
               float rim = exp(-abs(hsd) * 90.0);
               float halo = 1.0 - smoothstep(0.0, 0.07, hsd);
               diffuseColor.rgb += vec3(0.10, 0.22, 0.52) * halo * 0.7 * uGlow;
               diffuseColor.rgb += vec3(0.16, 0.30, 0.62) * rim * uGlow;
               diffuseColor.rgb *= 1.0 + 0.28 * fill * uGlow;
             }`,
          );
      };
      return m;
    }, [texture]);

    const onPointerMove = (e: ThreeEvent<PointerEvent>) => {
      e.stopPropagation();
      const uv = e.uv;
      if (!uv) return;
      // Smallest hotspot under the pointer wins, so a button nested in a
      // larger interactive region still takes precedence.
      let best: Hotspot | null = null;
      for (const s of hotspots) {
        if (
          uv.x >= s.x &&
          uv.x <= s.x + s.w &&
          uv.y >= s.y &&
          uv.y <= s.y + s.h &&
          (!best || s.w * s.h < best.w * best.h)
        ) {
          best = s;
        }
      }
      if (best) {
        glow.current.rect.set(best.x, best.y, best.w, best.h);
        glow.current.target = 1;
      } else {
        glow.current.target = 0;
      }
    };

    const onPointerOut = () => {
      glow.current.target = 0;
    };

    useFrame((_, dt) => {
      const g = glow.current;
      g.value = THREE.MathUtils.damp(g.value, g.target, 14, dt);
      if (uniforms.current) {
        uniforms.current.uGlow.value = g.value;
        uniforms.current.uRect.value.copy(g.rect);
      }
    });

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
        <mesh
          position={[0, 0, THICKNESS / 2 + 0.003]}
          material={faceMaterial}
          onPointerMove={onPointerMove}
          onPointerOut={onPointerOut}
        >
          <planeGeometry args={[w, h]} />
        </mesh>
      </group>
    );
  },
);
