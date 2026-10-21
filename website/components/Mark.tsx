/**
 * The mark: a closed book seen from the front, page edges behind, bound in claret with a
 * darker spine band, an author bar and hairline at the head, two title bars low down.
 */
export function Mark({ size = 28, className }: { size?: number; className?: string }) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 32 32"
      aria-hidden="true"
      focusable="false"
    >
      <rect x="9" y="4.5" width="18" height="24" rx="1.6" fill="#e9e1d2" />
      <rect x="6" y="3.5" width="19" height="25" rx="1.8" fill="#7a1f2b" />
      <rect x="6" y="3.5" width="3.2" height="25" rx="1.2" fill="#5e1621" />
      <rect x="11.5" y="7.5" width="8" height="1.3" rx="0.5" fill="#f1e6d4" opacity="0.85" />
      <rect x="11.5" y="10.2" width="11" height="0.6" fill="#f1e6d4" opacity="0.55" />
      <rect x="11.5" y="19.5" width="10" height="1.8" rx="0.6" fill="#f1e6d4" />
      <rect x="11.5" y="22.8" width="7" height="1.8" rx="0.6" fill="#f1e6d4" />
    </svg>
  );
}
