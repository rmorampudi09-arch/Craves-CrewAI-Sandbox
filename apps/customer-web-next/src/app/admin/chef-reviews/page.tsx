import { AdminChefReviewList } from "@/components/admin-chef-review-list";
import { AdminPageIntro } from "@/components/admin-page-intro";

export const metadata = { title: "Chef applications | Craves Admin", robots: { index: false, follow: false } };

export default function AdminChefReviewsPage() {
  return <div className="space-y-7">
    <AdminPageIntro eyebrow="Chef onboarding" title="Chef application review" description="Inspect the applicant profile and private proof files before recording an audited approval or rejection." />
    <AdminChefReviewList />
  </div>;
}
