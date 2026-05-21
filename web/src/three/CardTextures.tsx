/**
 * Rasterises every DOM card into a THREE texture.
 *
 * The cards are real React/Tailwind components. To use them as meshes in the
 * R3F scene we render them once into an off-screen pool, snapshot each node
 * with `html-to-image`, and wrap the resulting canvas in a CanvasTexture.
 * Cards keep transparent corners (border-radius) so the texture's alpha gives
 * the meshes rounded edges for free.
 */
import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { toCanvas } from "html-to-image";
import * as THREE from "three";
import { CARDS } from "../cards/registry";

/**
 * An interactive region inside a card, expressed in the mesh's UV space
 * (0..1, y measured from the bottom edge to match THREE plane UVs). Used to
 * raycast pointer hits against individual buttons for the hover glow.
 */
export interface Hotspot {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface CardTexture {
  texture: THREE.Texture;
  aspect: number;
  hotspots: Hotspot[];
}

/**
 * Measures every interactive element in a rendered card and converts its
 * bounding box into the card's UV space. Buttons, links and inputs are
 * picked up automatically; anything else can opt in with a `data-hot` attr.
 */
function collectHotspots(node: HTMLElement): Hotspot[] {
  const card = node.getBoundingClientRect();
  if (card.width < 2 || card.height < 2) return [];
  const hotspots: Hotspot[] = [];
  node
    .querySelectorAll<HTMLElement>("button, a, input, [data-hot]")
    .forEach((el) => {
      const r = el.getBoundingClientRect();
      if (r.width < 3 || r.height < 3) return;
      hotspots.push({
        x: (r.left - card.left) / card.width,
        // THREE plane UVs put v=0 at the bottom, so flip the DOM y axis.
        y: 1 - (r.bottom - card.top) / card.height,
        w: r.width / card.width,
        h: r.height / card.height,
      });
    });
  return hotspots;
}

const TextureContext = createContext<Map<string, CardTexture> | null>(null);

export function useCardTextures() {
  const ctx = useContext(TextureContext);
  if (!ctx) throw new Error("useCardTextures must be used within CardTextureProvider");
  return ctx;
}

export function CardTextureProvider({
  children,
  fallback,
}: {
  children: ReactNode;
  fallback: ReactNode;
}) {
  const poolRef = useRef<HTMLDivElement>(null);
  const [textures, setTextures] = useState<Map<string, CardTexture> | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function rasterise() {
      await document.fonts.ready;
      // Let layout settle before snapshotting.
      await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
      if (cancelled || !poolRef.current) return;

      const result = new Map<string, CardTexture>();
      for (const card of CARDS) {
        const node = poolRef.current.querySelector<HTMLElement>(
          `[data-card="${card.id}"]`,
        );
        if (!node) continue;
        // Measure hotspots before snapshotting — same live layout.
        const hotspots = collectHotspots(node);
        const canvas = await toCanvas(node, {
          pixelRatio: 2,
          // keep the area outside the card's border-radius transparent
          backgroundColor: undefined,
        });
        const texture = new THREE.CanvasTexture(canvas);
        texture.colorSpace = THREE.SRGBColorSpace;
        texture.anisotropy = 8;
        texture.needsUpdate = true;
        result.set(card.id, {
          texture,
          aspect: card.width / card.height,
          hotspots,
        });
      }
      if (!cancelled) setTextures(result);
    }

    rasterise();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <>
      {/* Off-screen rasterisation pool — rendered, just parked out of view. */}
      {!textures && (
        <div
          ref={poolRef}
          style={{
            position: "fixed",
            top: 0,
            left: "-12000px",
            display: "flex",
            flexWrap: "wrap",
            gap: 24,
          }}
          aria-hidden
        >
          {CARDS.map(({ id, Component }) => (
            <div key={id} data-card={id}>
              <Component />
            </div>
          ))}
        </div>
      )}

      {textures ? (
        <TextureContext.Provider value={textures}>
          {children}
        </TextureContext.Provider>
      ) : (
        fallback
      )}
    </>
  );
}
