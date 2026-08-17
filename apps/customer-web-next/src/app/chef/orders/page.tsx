import { ChefAccessBoundary } from "@/components/chef-access-boundary";
import { ChefOrderInbox } from "@/components/chef-order-inbox";
import { ChefPageHeader } from "@/components/chef-page-header";

export const metadata = {
  title: "Chef orders | Craves",
  robots: { index: false, follow: false },
};

export default function ChefOrdersPage() {
  return (
    <main className="mx-auto min-h-screen max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <ChefPageHeader
        eyebrow="Kitchen fulfilment"
        title="Chef order inbox"
        description="Review only the orders owned by your approved kitchen and take the workflow actions currently supported by Order Service. Delivery and refund transitions remain controlled by their owning services."
      />
      <div className="mt-6">
        <ChefAccessBoundary>
          <ChefOrderInbox />
        </ChefAccessBoundary>
      </div>
    </main>
  );
}
