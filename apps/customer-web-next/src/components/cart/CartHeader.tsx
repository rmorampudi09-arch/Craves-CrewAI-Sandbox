import { Link } from "@tanstack/react-router";
import { ArrowLeft } from "lucide-react";
import { CravesLogo } from "@/components/brand/CravesLogo";
import { PersistentCustomerServiceNav } from "@/components/navigation/PersistentCustomerServiceNav";

export function CartHeader({ onBack }: { onBack: () => void }) {
  return (
    <header className="sticky top-0 z-30 border-b border-border bg-white/95 backdrop-blur-xl">
      <div className="mx-auto flex min-h-18 max-w-5xl items-center gap-3 px-4 py-3 md:px-6">
        <button
          type="button"
          onClick={onBack}
          className="flex h-11 w-11 items-center justify-center rounded-lg border border-border bg-white text-ink transition-colors hover:border-primary"
          aria-label="Back to discovery"
        >
          <ArrowLeft className="h-5 w-5" aria-hidden="true" />
        </button>
        <Link to="/home" className="flex min-h-11 items-center gap-3 rounded-lg">
          <CravesLogo size="sm" />
          <span>
            <span className="block font-display text-lg font-bold tracking-[-0.03em] text-ink">Your cart</span>
            <span className="block text-xs text-muted-foreground">Live items and backend totals</span>
          </span>
        </Link>
      </div>
      <div className="mx-auto max-w-5xl px-4 pb-3 md:px-6">
        <PersistentCustomerServiceNav />
      </div>
    </header>
  );
}

export default CartHeader;
