/**
 * Standalone view of the three orb colour states, stacked vertically like
 * the reference mockup. Reachable at /#orbs so it can be reviewed without
 * disturbing the main card-deck page. Integration into the phone mock
 * (orb anchored bottom-centre) comes later.
 */
import { Canvas } from "@react-three/fiber";
import { Background } from "../Background";
import { OrbMesh } from "./OrbMesh";
import { ORB_PRESETS } from "./orbPresets";

export function OrbShowcase() {
  return (
    <>
      <Background />
      <Canvas
        camera={{ position: [0, 0, 11], fov: 42 }}
        gl={{ antialias: true, alpha: true }}
        dpr={[1, 2]}
        style={{ position: "relative" }}
      >
        <ambientLight intensity={1.15} />
        <directionalLight position={[3, 5, 4]} intensity={2} />
        <directionalLight
          position={[-4, -2, 2]}
          intensity={0.6}
          color="#4d8dff"
        />
        {ORB_PRESETS.map((state, i) => (
          <OrbMesh
            key={i}
            state={state}
            position={[0, (1 - i) * 2.7, 0]}
            scale={0.92}
          />
        ))}
      </Canvas>
    </>
  );
}
