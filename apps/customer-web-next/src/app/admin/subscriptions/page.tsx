import { AdminPageIntro } from "@/components/admin-page-intro";
import { AdminSubscriptionOperator } from "@/components/admin-subscription-operator";

export const metadata = { title: "Subscription operations | Craves Admin", robots: { index: false, follow: false } };

export default function AdminSubscriptionsPage() {
  return <div className="space-y-7">
    <AdminPageIntro eyebrow="Subscription operations" title="Controlled status intervention" description="Use an exact subscription UUID and record an explicit reason. Subscription Service validates the administrator role, allowed status and durable history." />
    <AdminSubscriptionOperator />
  </div>;
}
