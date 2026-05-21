/**
 * Standalone view of the tab core component. Reachable at /#tab.
 * Integration into the phone mock (right-rail anchored) comes later.
 */
import { Canvas } from "@react-three/fiber";
import { Background } from "../Background";
import { TabMesh } from "./TabMesh";
import { TAB_PRESETS } from "./tabPresets";

export function TabShowcase() {
  return (
    <>
      <Background />
      <Canvas
        camera={{ position: [0, 0, 7], fov: 42 }}
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
        <TabMesh state={TAB_PRESETS[0]} />
      </Canvas>
    </>
  );
}
