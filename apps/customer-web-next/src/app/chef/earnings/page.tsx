import { ChefAccessBoundary } from "@/components/chef-access-boundary";
import { ChefEarningsLedger } from "@/components/chef-earnings-ledger";
import { ChefPageHeader } from "@/components/chef-page-header";

export const metadata = {
  title: "Chef earnings | Craves",
  robots: { index: false, follow: false },
};

export default function ChefEarningsPage() {
  return (
    <main className="mx-auto min-h-screen max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <ChefPageHeader
        eyebrow="Finance ledger"
        title="Earnings and settlement status"
        description="Review your own administrator-approved earning allocations. Commission, tax withholding, adjustments and settlement timing remain finance-owned decisions; this workspace displays the audited backend ledger and never initiates a payout."
      />
      <div className="mt-6">
        <ChefAccessBoundary>
          <ChefEarningsLedger />
        </ChefAccessBoundary>
      </div>
    </main>
  );
}
