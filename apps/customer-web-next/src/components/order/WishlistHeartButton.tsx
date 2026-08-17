import { useEffect, useState } from "react";
import { Heart } from "lucide-react";
import {
  toggleWishlist,
  isWishlisted,
  subscribeWishlist,
  type WishlistItem,
} from "@/services/api/cravesWishlist";

interface WishlistHeartButtonProps {
  item: WishlistItem;
  className?: string;
  size?: "sm" | "md";
}

/**
 * Floating circular heart button. Click toggles the dish in/out of the
 * wishlist (src/services/api/cravesWishlist.ts) and stays in sync with the
 * saved state everywhere else it's shown (browse grid, dish detail, etc.).
 */
export function WishlistHeartButton({
  item,
  className = "",
  size = "sm",
}: WishlistHeartButtonProps) {
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    setSaved(isWishlisted(item.id));
    return subscribeWishlist(() => setSaved(isWishlisted(item.id)));
  }, [item.id]);

  const dim = size === "sm" ? "h-8 w-8" : "h-10 w-10";
  const iconDim = size === "sm" ? "h-4 w-4" : "h-5 w-5";

  return (
    <button
      type="button"
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        toggleWishlist(item);
      }}
      aria-pressed={saved}
      aria-label={saved ? "Remove from wishlist" : "Add to wishlist"}
      className={`flex ${dim} items-center justify-center rounded-full bg-white/90 shadow transition-transform hover:scale-105 ${className}`}
    >
      <Heart className={`${iconDim} ${saved ? "fill-red-500 text-red-500" : "text-ink"}`} />
    </button>
  );
}

export default WishlistHeartButton;
