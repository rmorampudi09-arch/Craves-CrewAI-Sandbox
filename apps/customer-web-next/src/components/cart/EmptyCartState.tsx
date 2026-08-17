import { ArrowRight, ShoppingBag } from "lucide-react";

export function EmptyCartState({ onBrowseMenu }: { onBrowseMenu: () => void }) {
  return (
    <div className="rounded-2xl border border-dashed border-border bg-white p-8 text-center shadow-[var(--shadow-card)] md:p-12">
      <span className="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-secondary text-primary">
        <ShoppingBag className="h-8 w-8" aria-hidden="true" />
      </span>
      <h1 className="mt-5 font-display text-2xl font-bold tracking-[-0.035em] text-ink">
        Your cart is empty
      </h1>
      <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-muted-foreground">
        Discover currently available dishes from home kitchens around your saved delivery address.
      </p>
      <button type="button" onClick={onBrowseMenu} className="btn-primary mt-6">
        Browse live menu <ArrowRight className="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  );
}

export default EmptyCartState;
