/**
 * Minimal stroke-icon set used across the FM card components.
 * Every icon inherits `currentColor` so cards control their own accent.
 */
import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement>;

function Svg({ children, ...props }: IconProps & { children: React.ReactNode }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      width={20}
      height={20}
      {...props}
    >
      {children}
    </svg>
  );
}

export const PhoneIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M5 4h3l1.5 4-2 1.5a12 12 0 0 0 5 5l1.5-2 4 1.5v3a2 2 0 0 1-2 2A14 14 0 0 1 4 6a2 2 0 0 1 1-2Z" />
  </Svg>
);

export const SendIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M21 3 3 10.5l7 2.5 2.5 7L21 3Z" />
    <path d="m10 13 4-4" />
  </Svg>
);

export const DocumentIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M6 3h8l5 5v13H6Z" />
    <path d="M14 3v5h5" />
    <path d="M9 13h7M9 17h7" />
  </Svg>
);

export const ChecklistIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 5h6v6H4Z" />
    <path d="m5 8 1.5 1.5L9 6.5" />
    <path d="M14 7h6M14 12h6M4 17h16" />
  </Svg>
);

export const CalendarIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 6h16v15H4Z" />
    <path d="M4 10h16M8 3v4M16 3v4" />
    <circle cx="12" cy="15" r="2.5" />
  </Svg>
);

export const NavigationIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M12 3 4 21l8-4 8 4Z" />
  </Svg>
);

export const MusicIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M9 18V5l10-2v13" />
    <circle cx="6" cy="18" r="3" />
    <circle cx="16" cy="16" r="3" />
  </Svg>
);

export const PersonIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="12" cy="8" r="4" />
    <path d="M4 21a8 8 0 0 1 16 0" />
  </Svg>
);

export const ImageIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M4 5h16v14H4Z" />
    <circle cx="9" cy="10" r="2" />
    <path d="m5 18 5-5 4 4 3-3 4 4" />
  </Svg>
);

export const QuoteIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M5 7h5v6a4 4 0 0 1-4 4M14 7h5v6a4 4 0 0 1-4 4" />
  </Svg>
);

export const ShieldIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M12 3 5 6v6c0 5 3 8 7 9 4-1 7-4 7-9V6Z" />
    <path d="m9 12 2 2 4-4" />
  </Svg>
);

export const GlobeIcon = (p: IconProps) => (
  <Svg {...p}>
    <circle cx="12" cy="12" r="9" />
    <path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18" />
  </Svg>
);

export const KebabIcon = (p: IconProps) => (
  <Svg {...p} strokeWidth={2}>
    <circle cx="12" cy="5" r="0.6" />
    <circle cx="12" cy="12" r="0.6" />
    <circle cx="12" cy="19" r="0.6" />
  </Svg>
);

export const CollapseIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M21 5v14M16 12H5M16 12l4-4M16 12l4 4" />
  </Svg>
);

export const PlusIcon = (p: IconProps) => (
  <Svg {...p}>
    <path d="M12 5v14M5 12h14" />
  </Svg>
);
