import { ChefModeDashboard } from "@/components/chef-mode-dashboard";
import { ChefPageHeader } from "@/components/chef-page-header";

export const metadata = {
  title: "Chef mode | Craves",
  robots: { index: false, follow: false },
};

export default function ChefModePage() {
  return (
    <main className="mx-auto min-h-screen max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <ChefPageHeader
        eyebrow="Approved chef workspace"
        title="Run your Craves kitchen"
        description="Manage only the application, kitchen, menu, orders, operations and financial ledger owned by your authenticated chef identity. Every service rechecks role and resource ownership."
      />
      <div className="mt-6">
        <ChefModeDashboard />
      </div>
    </main>
  );
}
