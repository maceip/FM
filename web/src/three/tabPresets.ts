/**
 * Example state for the tab core component, mirroring the reference mockup.
 * The orb reuses the cyan orb preset.
 */
import { ORB_PRESETS } from "./orbPresets";
import type { TabState } from "./TabMesh";

export const TAB_PRESETS: TabState[] = [
  {
    orb: ORB_PRESETS[1],
    title: "Syncing",
    lines: [
      { label: "Latency", value: "04ms" },
      { label: "Channel", value: "Ready" },
      { label: "Link", value: "Optimal" },
      { label: "Edge", value: "Online" },
    ],
  },
];
