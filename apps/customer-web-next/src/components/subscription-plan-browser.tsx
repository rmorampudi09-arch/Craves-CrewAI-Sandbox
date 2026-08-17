"use client";

import { useEffect, useState } from "react";
import type { PublicSubscriptionPlan } from "@/lib/subscription-contract";

function money(value: number, currency: string): string {
  try {
    return new Intl.NumberFormat("en-IN", { style: "currency", currency }).format(value);
  } catch {
    return `${currency} ${value.toFixed(2)}`;
  }
}

export function SubscriptionPlanBrowser() {
  const [plans, setPlans] = useState<PublicSubscriptionPlan[]>([]);
  const [message, setMessage] = useState("Loading meal plans…");

  useEffect(() => {
    let active = true;
    fetch("/api/subscriptions/plans", { cache: "no-store" })
      .then(async response => ({ response, body: await response.json().catch(() => null) }))
      .then(({ response, body }) => {
        if (!active) return;
        if (!response.ok) throw new Error("Meal plans are temporarily unavailable.");
        const items = body as PublicSubscriptionPlan[];
        setPlans(items);
        setMessage(items.length === 0 ? "No active meal plans are available yet." : "");
      })
      .catch(error => active && setMessage(error instanceof Error ? error.message : "Meal plans are temporarily unavailable."));
    return () => { active = false; };
  }, []);

  return <section className="meal-plans-legacy-ui">
    {message && <div className="rounded-[24px] bg-[#FFF8EC] p-6 text-slate-950" role="status">{message}</div>}
    <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
      {plans.map(plan => <article key={plan.id} className="rounded-[28px] bg-[#FFF8EC] p-6 text-slate-950 shadow-xl shadow-black/10">
        <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">{plan.billingPeriod.replace("_", " ")}</p>
        <h2 className="mt-3 text-2xl font-bold">{plan.name}</h2>
        <p className="mt-3 min-h-12 text-sm leading-6 text-slate-600">{plan.description ?? "Plan details are provided by the chef."}</p>
        <div className="mt-6 flex items-end justify-between gap-4">
          <strong className="text-2xl">{money(plan.amount, plan.currency)}</strong>
          <a
            href={`/subscriptions/new?planId=${encodeURIComponent(plan.id)}`}
            className="craves-button-link rounded-2xl px-4 py-3 text-sm"
          >
            Choose plan
          </a>
        </div>
        <p className="mt-4 text-xs text-slate-500">Plan code: {plan.planCode}</p>
      </article>)}
    </div>
  </section>;
}
