import { useState } from "react";

/**
 * Photographic backdrop behind the transparent Canvas.
 *
 * The dune photo lives at /background.webp (public/). The gradient fallback
 * approximates its mood — near-black sky, cool rim-light glow low-left — and
 * shows if the image ever fails to load.
 */
export function Background() {
  const [hasPhoto, setHasPhoto] = useState(true);
  return (
    <div className="fixed inset-0 -z-10" aria-hidden>
      <div className="scene-bg-gradient absolute inset-0" />
      {hasPhoto && (
        <img
          src="/background.webp"
          alt=""
          className="absolute inset-0 h-full w-full object-cover"
          onError={() => setHasPhoto(false)}
        />
      )}
      {/* vignette + floor lift so the card deck keeps its contrast */}
      <div className="scene-bg-veil absolute inset-0" />
    </div>
  );
}
