import { AdminPageIntro } from "@/components/admin-page-intro";
import { AdminSubscriptionPlanManager } from "@/components/admin-subscription-plan-manager";

export const metadata = { title: "Subscription plans | Craves Admin", robots: { index: false, follow: false } };

export default function AdminSubscriptionPlansPage() {
  return <div className="space-y-7">
    <AdminPageIntro eyebrow="Subscription configuration" title="Manage subscription plans" description="Create backend-owned draft plans and control only the existing DRAFT, ACTIVE and INACTIVE states. Product and Finance remain responsible for plan content and amount decisions." />
    <AdminSubscriptionPlanManager />
  </div>;
}
