import { ArrowRight, ShoppingCart } from "lucide-react";

interface FloatingCartBarProps {
  itemCount: number;
  onViewCart: () => void;
}

export function FloatingCartBar({ itemCount, onViewCart }: FloatingCartBarProps) {
  if (itemCount <= 0) return null;

  return (
    <div className="pointer-events-none fixed inset-x-0 bottom-4 z-40 px-4 sm:bottom-6">
      <button
        type="button"
        onClick={onViewCart}
        className="pointer-events-auto mx-auto flex min-h-14 w-full max-w-md items-center gap-3 rounded-2xl bg-ink px-5 text-left text-white shadow-[var(--shadow-pop)] transition-transform hover:-translate-y-1"
        aria-label={`View cart with ${itemCount} ${itemCount === 1 ? "item" : "items"}`}
      >
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary">
          <ShoppingCart className="h-5 w-5" aria-hidden="true" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-xs font-medium text-white/65">Ready to continue</span>
          <span className="block truncate text-sm font-semibold">
            {itemCount} {itemCount === 1 ? "item" : "items"} in your cart
          </span>
        </span>
        <span className="inline-flex items-center gap-1 text-sm font-semibold">
          View <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </span>
      </button>
    </div>
  );
}

export default FloatingCartBar;
