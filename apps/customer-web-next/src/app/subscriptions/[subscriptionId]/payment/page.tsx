import Link from "next/link";
import { SubscriptionRazorpayPayment } from "@/components/subscription-razorpay-payment";
import { isUuid } from "@/lib/server-api";

export const metadata = {
  title: "Meal plan payment | Craves",
  robots: { index: false, follow: false },
};

export default async function SubscriptionPaymentPage({
  params,
}: {
  params: Promise<{ subscriptionId: string }>;
}) {
  const { subscriptionId } = await params;

  return (
    <main className="mx-auto min-h-screen max-w-3xl px-5 py-12 sm:px-8">
      <Link href="/subscriptions" className="text-sm font-semibold text-[#F6B545]">
        ← My meal plans
      </Link>
      <h1 className="mt-8 text-4xl font-bold text-white sm:text-5xl">
        Complete your meal-plan payment.
      </h1>
      <p className="mt-4 text-slate-300">
        Razorpay handles payment details securely. Craves confirms the signed payment result before activating the subscription.
      </p>
      <div className="mt-8">
        {isUuid(subscriptionId) ? (
          <SubscriptionRazorpayPayment subscriptionId={subscriptionId} />
        ) : (
          <section className="rounded-[28px] bg-[#FFF8EC] p-6 text-slate-950">
            Invalid subscription ID.
          </section>
        )}
      </div>
    </main>
  );
}
