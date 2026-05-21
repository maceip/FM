/**
 * Standalone view of the Pixel 10 Fold outline, reachable at /#phone.
 * Anchoring the cards / tab / orb inside it comes with the layout wireframes.
 */
import { Canvas } from "@react-three/fiber";
import { Background } from "../Background";
import { PhoneFrame } from "./PhoneFrame";

export function PhoneShowcase() {
  return (
    <>
      <Background />
      <Canvas
        camera={{ position: [0, 0, 7.6], fov: 40 }}
        gl={{ antialias: true, alpha: true }}
        dpr={[1, 2]}
        style={{ position: "relative" }}
      >
        <PhoneFrame />
      </Canvas>
    </>
  );
}
