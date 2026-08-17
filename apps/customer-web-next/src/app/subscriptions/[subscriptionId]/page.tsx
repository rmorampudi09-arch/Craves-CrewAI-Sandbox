import Link from "next/link";
import { SubscriptionDetails } from "@/components/subscription-details";
import { isUuid } from "@/lib/server-api";

export const metadata = { title: "Subscription details | Craves", robots: { index: false, follow: false } };

export default async function SubscriptionDetailsPage({ params }: { params: Promise<{ subscriptionId: string }> }) {
  const { subscriptionId } = await params;
  return <main className="mx-auto min-h-screen max-w-3xl px-5 py-12 sm:px-8">
    <Link href="/subscriptions" className="text-sm font-semibold text-[#F6B545]">← My subscriptions</Link>
    <h1 className="mt-8 text-4xl font-bold text-white sm:text-5xl">Subscription details.</h1>
    <div className="mt-8">{isUuid(subscriptionId) ? <SubscriptionDetails subscriptionId={subscriptionId} /> : <section className="rounded-[28px] bg-[#FFF8EC] p-6 text-slate-950">Invalid subscription ID.</section>}</div>
  </main>;
}
