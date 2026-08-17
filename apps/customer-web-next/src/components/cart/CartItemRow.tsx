import { ImageOff, Minus, Plus, Trash2 } from "lucide-react";
import type { CartItem } from "@/services/api/cravesCart";

interface CartItemRowProps {
  item: CartItem;
  disabled?: boolean;
  onDecrease: () => void;
  onIncrease: () => void;
  onRemove: () => void;
}

function money(amount: number, currency: string): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(amount);
}

export function CartItemRow({
  item,
  disabled = false,
  onDecrease,
  onIncrease,
  onRemove,
}: CartItemRowProps) {
  return (
    <article className="grid gap-4 rounded-2xl border border-border bg-white p-4 shadow-[var(--shadow-card)] sm:grid-cols-[7rem_minmax(0,1fr)_auto] sm:items-center">
      <div className="relative flex aspect-square w-full items-center justify-center overflow-hidden rounded-xl bg-cream sm:w-28">
        <img
          src={item.img}
          alt={item.imageIsPlaceholder ? "" : item.name}
          aria-hidden={item.imageIsPlaceholder || undefined}
          className={
            item.imageIsPlaceholder
              ? "h-16 w-16 object-contain opacity-70"
              : "h-full w-full object-cover"
          }
        />
        {item.imageIsPlaceholder && (
          <span
            className="absolute bottom-2 right-2 rounded-full bg-white p-1 text-muted-foreground"
            title="Image not uploaded"
          >
            <ImageOff className="h-3.5 w-3.5" aria-hidden="true" />
          </span>
        )}
      </div>

      <div className="min-w-0">
        <h2 className="font-display text-lg font-bold leading-6 tracking-[-0.025em] text-ink">
          {item.name}
        </h2>
        <p className="mt-1 truncate text-sm text-muted-foreground">{item.chef}</p>
        <div className="mt-3 flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <span className="font-display text-lg font-bold text-ink">
            {money(item.lineTotal, item.currency)}
          </span>
          <span className="text-xs text-muted-foreground">
            {money(item.price, item.currency)} each
          </span>
        </div>
      </div>

      <div className="flex items-center justify-between gap-3 sm:flex-col sm:items-end">
        <div className="flex min-h-11 items-center rounded-lg border border-border bg-cream">
          <button
            type="button"
            onClick={onDecrease}
            disabled={disabled}
            className="flex h-11 w-11 items-center justify-center rounded-l-lg text-contrast-red hover:bg-secondary disabled:cursor-wait disabled:opacity-50"
            aria-label={`Decrease quantity of ${item.name}`}
          >
            <Minus className="h-4 w-4" aria-hidden="true" />
          </button>
          <span
            className="min-w-9 text-center text-sm font-bold text-ink"
            aria-live="polite"
          >
            {item.qty}
          </span>
          <button
            type="button"
            onClick={onIncrease}
            disabled={disabled || item.qty >= 50}
            className="flex h-11 w-11 items-center justify-center rounded-r-lg text-contrast-red hover:bg-secondary disabled:cursor-not-allowed disabled:opacity-50"
            aria-label={`Increase quantity of ${item.name}`}
          >
            <Plus className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
        <button
          type="button"
          onClick={onRemove}
          disabled={disabled}
          className="inline-flex min-h-11 items-center gap-2 rounded-lg px-3 text-sm font-semibold text-error hover:bg-error/5 disabled:cursor-wait disabled:opacity-50"
        >
          <Trash2 className="h-4 w-4" aria-hidden="true" /> Remove
        </button>
      </div>
    </article>
  );
}

export default CartItemRow;
