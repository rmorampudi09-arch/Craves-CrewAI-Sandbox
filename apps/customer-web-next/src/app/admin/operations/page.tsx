import { AdminOperationalInvestigator } from "@/components/admin-operational-investigator";
import { AdminPageIntro } from "@/components/admin-page-intro";

export const metadata = { title: "Operational investigations | Craves administration", robots: { index: false, follow: false } };

export default function AdminOperationsPage() {
  return <div className="space-y-7">
    <AdminPageIntro eyebrow="Craves operations" title="Audited operational evidence" description="Inspect one exact order, payment, refund or delivery command. Every successful lookup requires an operational reason and is recorded by the owning backend service." />
    <AdminOperationalInvestigator />
  </div>;
}
