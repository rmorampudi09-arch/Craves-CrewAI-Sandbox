import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { AdminChefReviewDetails } from "@/components/admin-chef-review-details";
import { AdminPageIntro } from "@/components/admin-page-intro";
import { isUuid } from "@/lib/server-api";

export const metadata = { title: "Chef review details | Craves Admin", robots: { index: false, follow: false } };

export default async function AdminChefReviewDetailsPage({ params }: { params: Promise<{ applicationId: string }> }) {
  const { applicationId } = await params;
  return <div className="space-y-7">
    <AdminPageIntro eyebrow="Chef onboarding" title="Review chef application" description="Open the submitted evidence and record only an approved backend decision.">
      <Link href="/admin/chef-reviews" className="inline-flex items-center gap-2 rounded-xl border border-[#d9cfdf] px-4 py-2.5 text-sm font-bold text-[#5d4e69]"><ArrowLeft size={16} />Applications</Link>
    </AdminPageIntro>
    {isUuid(applicationId)
      ? <AdminChefReviewDetails applicationId={applicationId} />
      : <section className="rounded-[28px] bg-white p-6 text-slate-950">Invalid application ID.</section>}
  </div>;
}
