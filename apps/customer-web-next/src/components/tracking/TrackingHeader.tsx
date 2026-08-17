import { ArrowLeft } from "lucide-react";
import { CravesLogo } from "@/components/brand/CravesLogo";
import { PersistentCustomerServiceNav } from "@/components/navigation/PersistentCustomerServiceNav";

interface TrackingHeaderProps {
  orderId: string;
  onBack: () => void;
}

export function TrackingHeader({ orderId, onBack }: TrackingHeaderProps) {
  return (
    <header className="sticky top-0 z-30 border-b border-border bg-white/95 backdrop-blur-xl">
      <div className="mx-auto flex min-h-18 max-w-5xl items-center gap-3 px-4 py-3 md:px-6">
        <button
          type="button"
          onClick={onBack}
          className="flex h-11 w-11 items-center justify-center rounded-lg border border-border bg-white text-ink transition-colors hover:border-primary"
          aria-label="Back to orders"
        >
          <ArrowLeft className="h-5 w-5" aria-hidden="true" />
        </button>
        <CravesLogo size="sm" decorative />
        <div className="min-w-0">
          <h1 className="truncate font-display text-lg font-bold tracking-[-0.03em] text-ink">
            Track order
          </h1>
          <p className="text-xs text-muted-foreground">
            Order #{orderId.slice(-8).toUpperCase()}
          </p>
        </div>
      </div>
      <div className="mx-auto max-w-5xl px-4 pb-3 md:px-6">
        <PersistentCustomerServiceNav />
      </div>
    </header>
  );
}

export default TrackingHeader;
