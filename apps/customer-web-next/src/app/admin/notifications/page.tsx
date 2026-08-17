import { AdminNotificationRecovery } from "@/components/admin-notification-recovery";
import { AdminPageIntro } from "@/components/admin-page-intro";

export const dynamic = "force-dynamic";

export default function AdminNotificationsPage() {
  return <div className="space-y-7">
    <AdminPageIntro eyebrow="Notification operations" title="Failed delivery recovery" description="Inspect bounded FAILED or DEAD_LETTER requests and return one audited request to PENDING without calling an external provider in the administrator transaction." />
    <AdminNotificationRecovery />
  </div>;
}
