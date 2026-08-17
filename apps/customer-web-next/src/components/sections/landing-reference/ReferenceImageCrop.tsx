import Image from "next/image";
import type { CSSProperties } from "react";

import styles from "@/screens/public/LandingPage/LandingV2.module.css";

interface ReferenceImageCropProps {
  src: string;
  sourceWidth: number;
  sourceHeight: number;
  crop: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
  alt?: string;
  className?: string;
  priority?: boolean;
  sizes?: string;
}

/**
 * Displays an untouched region of an approved reference PNG.
 *
 * The original image file is never edited or re-encoded. The container only
 * clips the source image at render time so native HTML can own the surrounding
 * text and controls while the approved illustration pixels remain unchanged.
 */
export function ReferenceImageCrop({
  src,
  sourceWidth,
  sourceHeight,
  crop,
  alt = "",
  className = "",
  priority = false,
  sizes = "(min-width: 1024px) 50vw, 100vw",
}: ReferenceImageCropProps) {
  const imageStyle: CSSProperties = {
    width: `${(sourceWidth / crop.width) * 100}%`,
    height: "auto",
    left: `${-(crop.x / crop.width) * 100}%`,
    top: `${-(crop.y / crop.height) * 100}%`,
  };

  return (
    <div
      className={`${styles.referenceCrop} ${className}`}
      style={{ aspectRatio: `${crop.width} / ${crop.height}` }}
      role={alt ? "img" : undefined}
      aria-label={alt || undefined}
      aria-hidden={alt ? undefined : true}
    >
      <Image
        src={src}
        width={sourceWidth}
        height={sourceHeight}
        alt=""
        priority={priority}
        sizes={sizes}
        unoptimized
        className={styles.referenceCropImage}
        style={imageStyle}
        draggable={false}
      />
    </div>
  );
}

export default ReferenceImageCrop;
