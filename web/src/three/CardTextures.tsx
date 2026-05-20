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

export interface CardTexture {
  texture: THREE.Texture;
  aspect: number;
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
        const canvas = await toCanvas(node, {
          pixelRatio: 2,
          // keep the area outside the card's border-radius transparent
          backgroundColor: undefined,
        });
        const texture = new THREE.CanvasTexture(canvas);
        texture.colorSpace = THREE.SRGBColorSpace;
        texture.anisotropy = 8;
        texture.needsUpdate = true;
        result.set(card.id, { texture, aspect: card.width / card.height });
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
