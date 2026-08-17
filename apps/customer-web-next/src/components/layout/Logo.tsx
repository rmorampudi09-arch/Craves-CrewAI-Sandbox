import { CravesLogo } from "@/components/brand/CravesLogo";

/**
 * Canonical Craves logo used by the public landing navigation and footer.
 *
 * The previous implementation mixed a legacy PNG with a second text wordmark
 * and tagline. That produced a different brand lockup from the approved red
 * rounded-square asset used elsewhere in the application. Keep every caller on
 * the single approved asset instead.
 */
export function Logo(props: { light?: boolean }) {
  // Preserve the existing component API while keeping one identical logo in
  // light and standard layouts. The approved asset already supplies its own
  // red background and white lettering.
  void props.light;

  return (
    <span className="inline-flex items-center" aria-label="Craves">
      <CravesLogo size="lg" decorative priority className="h-14 w-14" />
    </span>
  );
}

export default Logo;
