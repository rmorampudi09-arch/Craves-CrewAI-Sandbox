import Image from "next/image";

type LogoSize = "sm" | "md" | "lg";

interface CravesLogoProps {
  size?: LogoSize;
  decorative?: boolean;
  className?: string;
  priority?: boolean;
}

const dimensions: Record<LogoSize, number> = {
  sm: 32,
  md: 40,
  lg: 56,
};

/**
 * Single canonical Craves logo for customer and chef web experiences.
 *
 * The versioned path prevents browsers and edge caches from retaining an older
 * logo after deployment. The PNG is deterministically extracted from the
 * approved cropped source during development and production builds.
 */
export function CravesLogo({
  size = "md",
  decorative = false,
  className = "",
  priority = false,
}: CravesLogoProps) {
  const dimension = dimensions[size];

  return (
    <Image
      src="/brand/craves-logo-20260805.png"
      width={dimension}
      height={dimension}
      alt={decorative ? "" : "Craves"}
      aria-hidden={decorative || undefined}
      priority={priority}
      unoptimized
      className={`shrink-0 object-contain ${className}`.trim()}
    />
  );
}

export default CravesLogo;
