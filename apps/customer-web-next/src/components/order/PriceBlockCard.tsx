interface PriceBlockCardProps {
  price: number;
  originalPrice?: number;
}

/** Tan price card: "X% off" badge, current price, strikethrough original, "Per Serve". */
export function PriceBlockCard({ price, originalPrice }: PriceBlockCardProps) {
  const discountPercent =
    originalPrice && originalPrice > price ? Math.round((1 - price / originalPrice) * 100) : 0;

  return (
    <div className="shrink-0 rounded-xl bg-accent/60 px-4 py-3 text-right">
      {discountPercent > 0 && (
        <span className="mb-1 inline-block rounded bg-primary px-2 py-0.5 text-[10px] font-bold text-primary-foreground">
          {discountPercent}% OFF
        </span>
      )}
      <p className="font-display text-2xl font-bold text-ink">₹{price}</p>
      {originalPrice && originalPrice > price && (
        <p className="text-xs text-muted-foreground line-through">₹{originalPrice}</p>
      )}
      <p className="text-[11px] text-muted-foreground">Per Serve</p>
    </div>
  );
}

export default PriceBlockCard;
