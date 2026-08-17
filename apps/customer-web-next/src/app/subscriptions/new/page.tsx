import Link from "next/link";
import { SubscriptionEnrollmentForm } from "@/components/subscription-enrollment-form";
import { isUuid } from "@/lib/server-api";

export const metadata = { title: "Start a subscription | Craves", robots: { index: false, follow: false } };

export default async function NewSubscriptionPage({ searchParams }: { searchParams: Promise<{ planId?: string }> }) {
  const { planId } = await searchParams;
  return <main className="mx-auto min-h-screen max-w-3xl px-5 py-12 sm:px-8">
    <Link href="/subscriptions/plans" className="text-sm font-semibold text-[#F6B545]">← Meal plans</Link>
    <h1 className="mt-8 text-4xl font-bold text-white sm:text-5xl">Start your meal subscription.</h1>
    <p className="mt-4 text-slate-300">Choose a saved address and a start date. Subscription Service remains authoritative for status and scheduling.</p>
    <div className="mt-8">{planId && isUuid(planId) ? <SubscriptionEnrollmentForm planId={planId} /> : <section className="rounded-[28px] bg-[#FFF8EC] p-6 text-slate-950">Select an active plan first.</section>}</div>
  </main>;
}
