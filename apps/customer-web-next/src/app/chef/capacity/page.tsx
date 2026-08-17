import { ChefAccessBoundary } from "@/components/chef-access-boundary";
import { ChefCapacityQuickSetup } from "@/components/chef-capacity-quick-setup";
import { ChefPageHeader } from "@/components/chef-page-header";

export const metadata = {
  title: "Subscription availability | Craves",
  robots: { index: false, follow: false },
};

export default function ChefCapacityPage() {
  return (
    <main className="mx-auto min-h-screen max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <ChefPageHeader
        eyebrow="Subscription availability"
        title="Choose availability only when you want to customize it"
        description="Craves automatically creates safe missing limits when you submit a meal plan. Use this single workspace only when you want to choose specific weekdays, menu items, a calendar date, or your own limits."
      />
      <div className="mt-6">
        <ChefAccessBoundary>
          <ChefCapacityQuickSetup />
        </ChefAccessBoundary>
      </div>
    </main>
  );
}
