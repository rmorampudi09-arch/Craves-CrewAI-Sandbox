import { LoaderCircle, Minus, Plus, ShoppingCart } from "lucide-react";

interface DishBottomBarProps {
  price: number;
  quantity: number;
  onDecrease: () => void;
  onIncrease: () => void;
  onAddToCart: () => void;
  disabled?: boolean;
}

function priceLabel(price: number): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(price);
}

export function DishBottomBar({
  price,
  quantity,
  onDecrease,
  onIncrease,
  onAddToCart,
  disabled = false,
}: DishBottomBarProps) {
  return (
    <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-white/95 shadow-[0_-8px_32px_rgba(0,0,0,0.08)] backdrop-blur-xl">
      <div className="mx-auto flex max-w-3xl flex-wrap items-center gap-3 px-4 py-3 md:px-6">
        <div className="min-w-[7rem]">
          <p className="text-[0.68rem] font-semibold uppercase tracking-[0.08em] text-muted-foreground">Item total</p>
          <p className="font-display text-xl font-bold text-ink">{priceLabel(price)}</p>
        </div>
        <div className="flex min-h-11 items-center rounded-lg border border-border bg-cream">
          <button
            type="button"
            onClick={onDecrease}
            disabled={quantity <= 1 || disabled}
            className="flex h-11 w-11 items-center justify-center rounded-l-lg text-contrast-red transition-colors hover:bg-secondary disabled:cursor-not-allowed disabled:opacity-40"
            aria-label="Decrease quantity"
          >
            <Minus className="h-4 w-4" aria-hidden="true" />
          </button>
          <span className="min-w-8 text-center text-sm font-bold text-ink" aria-live="polite">{quantity}</span>
          <button
            type="button"
            onClick={onIncrease}
            disabled={disabled}
            className="flex h-11 w-11 items-center justify-center rounded-r-lg text-contrast-red transition-colors hover:bg-secondary disabled:cursor-not-allowed disabled:opacity-40"
            aria-label="Increase quantity"
          >
            <Plus className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
        <button
          type="button"
          onClick={onAddToCart}
          disabled={disabled}
          className="btn-primary ml-auto min-h-12 flex-1 sm:flex-none sm:px-8 disabled:cursor-wait disabled:opacity-60"
        >
          {disabled ? (
            <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
          ) : (
            <ShoppingCart className="h-4 w-4" aria-hidden="true" />
          )}
          {disabled ? "Adding…" : "Add to cart"}
        </button>
      </div>
    </div>
  );
}

export default DishBottomBar;
