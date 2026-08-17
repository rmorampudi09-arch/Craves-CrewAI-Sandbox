import { ChefAccessBoundary } from "@/components/chef-access-boundary";
import { ChefKitchenForm } from "@/components/chef-kitchen-form";
import { ChefPageHeader } from "@/components/chef-page-header";

export const metadata = {
  title: "Kitchen profile | Craves",
  robots: { index: false, follow: false },
};

export default function ChefKitchenPage() {
  return (
    <main className="mx-auto min-h-screen max-w-6xl px-4 py-6 md:px-6 md:py-8">
      <ChefPageHeader
        eyebrow="Catalog kitchen"
        title="Kitchen profile and availability"
        description="Manage the Catalog Service profile owned by your approved chef identity. Kitchen status and coordinates determine operational discovery; serviceability, ranking and delivery radius remain backend and product decisions."
      />
      <div className="mt-6">
        <ChefAccessBoundary>
          <ChefKitchenForm />
        </ChefAccessBoundary>
      </div>
    </main>
  );
}
