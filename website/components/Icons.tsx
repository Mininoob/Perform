/* A small authored icon set: 1.8 stroke, round caps and joins, like the apps. */

type Props = { className?: string };

const line = {
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.8,
  strokeLinecap: "round" as const,
  strokeLinejoin: "round" as const,
};

export function GitHubIcon({ className }: Props) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path
        fill="currentColor"
        d="M12 1.6a10.4 10.4 0 0 0-3.29 20.27c.52.1.71-.23.71-.5v-1.75c-2.9.63-3.51-1.4-3.51-1.4-.47-1.2-1.16-1.52-1.16-1.52-.94-.65.07-.64.07-.64 1.05.08 1.6 1.08 1.6 1.08.93 1.6 2.44 1.14 3.04.87.09-.68.36-1.14.66-1.4-2.31-.27-4.75-1.16-4.75-5.15 0-1.14.4-2.07 1.08-2.8-.11-.27-.47-1.33.1-2.76 0 0 .88-.28 2.86 1.07a9.9 9.9 0 0 1 5.2 0c1.98-1.35 2.86-1.07 2.86-1.07.57 1.43.21 2.49.1 2.76.67.73 1.08 1.66 1.08 2.8 0 4-2.44 4.88-4.77 5.14.38.32.71.96.71 1.94v2.87c0 .28.19.6.72.5A10.4 10.4 0 0 0 12 1.6Z"
      />
    </svg>
  );
}

export function DownloadIcon({ className }: Props) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true" focusable="false" {...line}>
      <path d="M12 4v11" />
      <path d="m7.5 10.5 4.5 4.5 4.5-4.5" />
      <path d="M5 19.5h14" />
    </svg>
  );
}

export function ArrowDownIcon({ className }: Props) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true" focusable="false" {...line}>
      <path d="M12 5v14" />
      <path d="m6.5 13.5 5.5 5.5 5.5-5.5" />
    </svg>
  );
}

export function ArrowRightIcon({ className }: Props) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true" focusable="false" {...line}>
      <path d="M5 12h14" />
      <path d="m13.5 6.5 5.5 5.5-5.5 5.5" />
    </svg>
  );
}

export function PhoneIcon({ className }: Props) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true" focusable="false" {...line}>
      <rect x="6.5" y="2.5" width="11" height="19" rx="2.2" />
      <path d="M10.5 18.5h3" />
    </svg>
  );
}

export function ComputerIcon({ className }: Props) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true" focusable="false" {...line}>
      <rect x="3" y="4" width="18" height="12.5" rx="1.6" />
      <path d="M9 20h6" />
      <path d="M12 16.5V20" />
    </svg>
  );
}

export function FolderIcon({ className }: Props) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true" focusable="false" {...line}>
      <path d="M3.5 7v11A1.5 1.5 0 0 0 5 19.5h14a1.5 1.5 0 0 0 1.5-1.5V9.5A1.5 1.5 0 0 0 19 8h-7.5l-2-2.5H5A1.5 1.5 0 0 0 3.5 7Z" />
    </svg>
  );
}

export function CheckIcon({ className }: Props) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true" focusable="false" {...line}>
      <path d="m5 12.5 5 5 9-10.5" />
    </svg>
  );
}

export function SearchIcon({ className }: Props) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true" focusable="false" {...line}>
      <circle cx="11" cy="11" r="6" />
      <path d="m16 16 4 4" />
    </svg>
  );
}

export function PlusIcon({ className }: Props) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true" focusable="false" {...line}>
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

export function GearIcon({ className }: Props) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true" focusable="false" {...line}>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 3v2.2M12 18.8V21M3 12h2.2M18.8 12H21M5.6 5.6l1.6 1.6M16.8 16.8l1.6 1.6M5.6 18.4l1.6-1.6M16.8 7.2l1.6-1.6" />
    </svg>
  );
}

export function LockIcon({ className }: Props) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true" focusable="false" {...line}>
      <rect x="5" y="10.5" width="14" height="10" rx="1.8" />
      <path d="M8.5 10.5V7.8a3.5 3.5 0 0 1 7 0v2.7" />
    </svg>
  );
}
