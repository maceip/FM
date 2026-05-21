/**
 * Standalone view of the Pixel 10 Fold frame, reachable at /#phone.
 * Shown at a slight 3/4 angle so the rail and thickness read. Anchoring
 * the cards / tab / orb inside it comes with the layout wireframes.
 */
import { Canvas } from "@react-three/fiber";
import { Environment, Lightformer } from "@react-three/drei";
import { Background } from "../Background";
import { PhoneFrame } from "./PhoneFrame";

export function PhoneShowcase() {
  return (
    <>
      <Background />
      <Canvas
        camera={{ position: [0, 0, 7.6], fov: 40 }}
        gl={{ antialias: true, alpha: true, toneMappingExposure: 1.25 }}
        dpr={[1, 2]}
        style={{ position: "relative" }}
      >
        <ambientLight intensity={0.9} />
        <directionalLight position={[4, 5, 6]} intensity={2} />
        <directionalLight
          position={[-5, 2, 4]}
          intensity={1}
          color="#7ba2ff"
        />

        {/* Procedural studio environment — gives the metal rail and glass
            something to reflect. Lightformers are in-scene geometry, so
            this needs no HDR download. */}
        <Environment resolution={256}>
          <Lightformer
            intensity={3}
            position={[0, 4, 3]}
            scale={[8, 3, 1]}
            color="#dce8ff"
          />
          <Lightformer
            intensity={2}
            position={[-5, 1, 2]}
            scale={[3, 8, 1]}
            color="#6b9bff"
          />
          <Lightformer
            intensity={1.6}
            position={[5, -3, 2]}
            scale={[5, 5, 1]}
            color="#ffffff"
          />
        </Environment>

        <PhoneFrame rotation={[0.07, -0.26, 0]} />
      </Canvas>
    </>
  );
}
