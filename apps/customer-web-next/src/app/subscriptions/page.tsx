import Link from "next/link";
import { PersistentCustomerServiceNav } from "@/components/navigation/PersistentCustomerServiceNav";
import { SubscriptionManager } from "@/components/subscription-manager";

export const metadata = {
  title: "My subscriptions | Craves",
  robots: { index: false, follow: false },
};

export default function SubscriptionsPage() {
  return (
    <div className="min-h-screen">
      <header className="border-b border-border bg-white">
        <div className="mx-auto max-w-5xl px-5 py-3 sm:px-8">
          <PersistentCustomerServiceNav />
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-5 py-12 sm:px-8">
        <Link href="/home" className="text-sm font-semibold text-[#F6B545]">
          ← Craves home
        </Link>
        <p className="mt-8 text-sm font-bold uppercase tracking-[0.22em] text-[#F6B545]">
          Customer subscriptions
        </p>
        <h1 className="mt-4 text-4xl font-bold text-white sm:text-6xl">
          Your meal plans.
        </h1>
        <p className="mt-5 max-w-3xl text-slate-300">
          Subscription Service owns status and service dates. This page exposes only pause and cancel, which are the currently supported customer transitions.
        </p>
        <div className="mt-10">
          <SubscriptionManager />
        </div>
      </main>
    </div>
  );
}
