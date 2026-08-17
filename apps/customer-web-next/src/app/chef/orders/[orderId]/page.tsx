import { ChefAccessBoundary } from "@/components/chef-access-boundary";
import { ChefOrderDetails } from "@/components/chef-order-details";
import { ChefPageHeader } from "@/components/chef-page-header";

export const metadata = {
  title: "Chef order | Craves",
  robots: { index: false, follow: false },
};

export default async function ChefOrderPage({
  params,
}: {
  params: Promise<{ orderId: string }>;
}) {
  const { orderId } = await params;
  return (
    <main className="mx-auto min-h-screen max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <ChefPageHeader
        eyebrow={`Order #${orderId.slice(-8).toUpperCase()}`}
        title="Order detail and kitchen actions"
        description="Review the backend snapshot, customer fulfilment details, charges and the chef transitions currently allowed by Order Service."
      />
      <div className="mt-6">
        <ChefAccessBoundary>
          <ChefOrderDetails orderId={orderId} />
        </ChefAccessBoundary>
      </div>
    </main>
  );
}
