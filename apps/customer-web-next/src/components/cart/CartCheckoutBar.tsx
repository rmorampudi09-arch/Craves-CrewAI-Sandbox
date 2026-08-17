import { ArrowRight, LoaderCircle } from "lucide-react";

interface CartCheckoutBarProps {
  total: number;
  currency: string;
  disabled?: boolean;
  onContinue: () => void;
}

function money(amount: number, currency: string): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(amount);
}

export function CartCheckoutBar({
  total,
  currency,
  disabled = false,
  onContinue,
}: CartCheckoutBarProps) {
  return (
    <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-white/95 shadow-[0_-8px_32px_rgba(0,0,0,0.08)] backdrop-blur-xl">
      <div className="mx-auto flex max-w-5xl items-center gap-4 px-4 py-3 md:px-6">
        <div className="min-w-0">
          <p className="text-[0.68rem] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
            Food subtotal
          </p>
          <p className="font-display text-xl font-bold text-ink">
            {money(total, currency)}
          </p>
        </div>
        <button
          type="button"
          onClick={onContinue}
          disabled={disabled}
          className="btn-primary ml-auto min-h-12 flex-1 px-6 sm:flex-none disabled:cursor-wait disabled:opacity-60"
        >
          {disabled ? (
            <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />
          ) : (
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          )}
          {disabled ? "Checking cart…" : "Choose address"}
        </button>
      </div>
    </div>
  );
}

export default CartCheckoutBar;
