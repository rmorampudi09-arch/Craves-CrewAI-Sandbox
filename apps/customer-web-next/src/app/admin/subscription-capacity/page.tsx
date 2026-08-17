import { AdminPageIntro } from "@/components/admin-page-intro";
import { AdminSubscriptionCapacityOperator } from "@/components/admin-subscription-capacity-operator";

export const metadata = {
  title: "Subscription capacity | Craves Admin",
  robots: { index: false, follow: false },
};

export default function AdminSubscriptionCapacityPage() {
  return <div className="space-y-7">
    <AdminPageIntro
      eyebrow="Capacity operations"
      title="Protect confirmed subscribers without allowing overbooking"
      description="Inspect chef-owned capacity and deficits, freeze new subscription sales during an incident, and run audited reconciliation. Support cannot increase a chef's declared capacity from this workspace."
    />
    <AdminSubscriptionCapacityOperator />
  </div>;
}
