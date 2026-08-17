import { ChefAccessBoundary } from "@/components/chef-access-boundary";
import { ChefOperationsWorkspace } from "@/components/chef-operations-workspace";
import { ChefPageHeader } from "@/components/chef-page-header";

export const metadata = {
  title: "Chef operations | Craves",
  robots: { index: false, follow: false },
};

export default function ChefOperationsPage() {
  return (
    <main className="mx-auto min-h-screen max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <ChefPageHeader
        eyebrow="Readiness and compliance"
        title="Kitchen operations"
        description="Review the real backend states that control chef approval, proof evidence, kitchen discovery, location readiness, menu availability and customer-facing images. Unsupported weekly schedules or legal eligibility rules are never fabricated."
      />
      <div className="mt-6">
        <ChefAccessBoundary>
          <ChefOperationsWorkspace />
        </ChefAccessBoundary>
      </div>
    </main>
  );
}
