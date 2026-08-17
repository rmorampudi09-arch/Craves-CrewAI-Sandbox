import { ReceiptText, ShieldCheck } from "lucide-react";

interface BillSummaryCardProps {
  subtotal: number;
  currency?: string;
}

function money(amount: number, currency: string): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(amount);
}

export function BillSummaryCard({
  subtotal,
  currency = "INR",
}: BillSummaryCardProps) {
  return (
    <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]">
      <div className="flex items-center gap-3">
        <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-secondary text-primary">
          <ReceiptText className="h-5 w-5" aria-hidden="true" />
        </span>
        <div>
          <p className="craves-overline text-primary">Current cart</p>
          <h2 className="font-display text-lg font-bold text-ink">Bill preview</h2>
        </div>
      </div>
      <dl className="mt-5 border-y border-border py-4">
        <div className="flex items-center justify-between gap-4">
          <dt className="text-sm text-muted-foreground">Food subtotal</dt>
          <dd className="font-display text-lg font-bold text-ink">
            {money(subtotal, currency)}
          </dd>
        </div>
      </dl>
      <p className="mt-4 flex items-start gap-2 text-xs leading-5 text-muted-foreground">
        <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-success" aria-hidden="true" />
        Delivery fee, platform fee, tax and final total are calculated by the Order Service after a saved delivery address is selected.
      </p>
    </section>
  );
}

export default BillSummaryCard;
