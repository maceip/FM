/**
 * Example colour states for the orb. The orb's only behaviour is colour, so
 * a "preset" is just the per-chit colour arrays plus a centre colour/icon.
 * These three mirror the reference mockup; new states are made the same way.
 */
import {
  ORB_INNER_COUNT,
  ORB_OUTER_COUNT,
  type OrbState,
} from "./OrbMesh";

interface RingOpts {
  count: number;
  hueFrom: number;
  hueTo: number;
  sat: number;
  baseLight: number;
  crestLight: number;
  /** Where around the ring (0..1) the gauge is brightest. */
  crestAt: number;
}

/** Builds a gauge-style ring: a hue sweep with one bright crest. */
function ring({
  count,
  hueFrom,
  hueTo,
  sat,
  baseLight,
  crestLight,
  crestAt,
}: RingOpts): string[] {
  return Array.from({ length: count }, (_, i) => {
    const t = i / count;
    const hue = (((hueFrom + (hueTo - hueFrom) * t) % 360) + 360) % 360;
    // shortest wrapped distance to the crest, so the crest can straddle 0
    let d = Math.abs(t - crestAt);
    d = Math.min(d, 1 - d);
    const crest = Math.exp(-Math.pow(d / 0.3, 2));
    const light = baseLight + (crestLight - baseLight) * crest;
    return `hsl(${hue.toFixed(1)}, ${sat}%, ${light.toFixed(1)}%)`;
  });
}

export const ORB_PRESETS: OrbState[] = [
  {
    // emerald — green dot
    outerColors: ring({
      count: ORB_OUTER_COUNT,
      hueFrom: 152,
      hueTo: 82,
      sat: 82,
      baseLight: 22,
      crestLight: 62,
      crestAt: 0.72,
    }),
    innerColors: ring({
      count: ORB_INNER_COUNT,
      hueFrom: 158,
      hueTo: 96,
      sat: 70,
      baseLight: 18,
      crestLight: 48,
      crestAt: 0.72,
    }),
    centerColor: "#4ce58f",
    icon: "dot",
  },
  {
    // cyan — blue square
    outerColors: ring({
      count: ORB_OUTER_COUNT,
      hueFrom: 198,
      hueTo: 166,
      sat: 88,
      baseLight: 24,
      crestLight: 64,
      crestAt: 0.6,
    }),
    innerColors: ring({
      count: ORB_INNER_COUNT,
      hueFrom: 202,
      hueTo: 176,
      sat: 78,
      baseLight: 20,
      crestLight: 50,
      crestAt: 0.6,
    }),
    centerColor: "#54d6ff",
    icon: "square",
  },
  {
    // amber → magenta — alert
    outerColors: ring({
      count: ORB_OUTER_COUNT,
      hueFrom: 52,
      hueTo: -52,
      sat: 90,
      baseLight: 26,
      crestLight: 60,
      crestAt: 0.34,
    }),
    innerColors: ring({
      count: ORB_INNER_COUNT,
      hueFrom: 48,
      hueTo: -40,
      sat: 80,
      baseLight: 22,
      crestLight: 48,
      crestAt: 0.34,
    }),
    centerColor: "#ff9b3c",
    icon: "alert",
  },
];
