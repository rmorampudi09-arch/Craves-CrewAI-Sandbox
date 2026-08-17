"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import type { CustomerSubscription } from "@/lib/subscription-contract";

export function SubscriptionManager() {
  const [items, setItems] = useState<CustomerSubscription[]>([]);
  const [message, setMessage] = useState("Loading subscriptions…");

  const load = useCallback(async () => {
    const response = await fetch("/api/subscriptions", { cache: "no-store" });
    const body = await response.json().catch(() => null);
    if (response.status === 401) throw new Error("Your session expired. Sign in again.");
    if (!response.ok) throw new Error("Subscriptions are temporarily unavailable.");
    const subscriptions = body as CustomerSubscription[];
    setItems(subscriptions);
    setMessage(subscriptions.length ? "" : "You do not have a meal subscription yet.");
  }, []);

  useEffect(() => {
    void load().catch(error =>
      setMessage(error instanceof Error ? error.message : "Subscriptions are unavailable."),
    );
  }, [load]);

  return (
    <section>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <Link href="/subscriptions/plans" className="rounded-2xl bg-[#6930CA] px-5 py-3 font-bold text-white">
          Browse plans
        </Link>
        <button
          type="button"
          onClick={() => void load()}
          className="rounded-2xl border border-white/20 px-5 py-3 font-bold text-white"
        >
          Refresh
        </button>
      </div>
      {message && (
        <div className="rounded-[24px] bg-[#FFF8EC] p-6 text-slate-950" role="status">
          {message}
        </div>
      )}
      <div className="space-y-5">
        {items.map(item => {
          const paymentRequired = item.status === "PENDING_PAYMENT" || item.status === "PAYMENT_FAILED";
          return (
            <article key={item.id} className="rounded-[28px] bg-[#FFF8EC] p-6 text-slate-950">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">
                    {item.status.replaceAll("_", " ")}
                  </p>
                  <h2 className="mt-2 text-2xl font-bold">Meal subscription</h2>
                  <p className="mt-2 text-sm text-slate-600">
                    Starts {new Date(`${item.startDate}T00:00:00Z`).toLocaleDateString("en-IN")}
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  {paymentRequired && (
                    <Link
                      href={`/subscriptions/${item.id}/payment`}
                      className="rounded-2xl bg-[#6930CA] px-4 py-2 text-sm font-bold text-white"
                    >
                      {item.status === "PAYMENT_FAILED" ? "Retry payment" : "Pay now"}
                    </Link>
                  )}
                  <Link
                    href={`/subscriptions/${item.id}`}
                    className="rounded-2xl border border-[#6930CA] px-4 py-2 text-sm font-bold text-[#6930CA]"
                  >
                    View & manage
                  </Link>
                </div>
              </div>
              <dl className="mt-5 grid gap-3 text-sm sm:grid-cols-2">
                <div>
                  <dt className="text-slate-500">Next service date</dt>
                  <dd className="font-semibold">{item.nextServiceDate ?? "Not scheduled"}</dd>
                </div>
                <div>
                  <dt className="text-slate-500">Delivery address</dt>
                  <dd className="font-semibold">{item.deliveryAddressId ? "Saved address selected" : "Not selected"}</dd>
                </div>
              </dl>
              <p className="mt-4 text-xs text-slate-500">
                Pause, resume, skip and cancellation controls are shown in details only when the administrator-approved policy and cutoff permit them.
              </p>
            </article>
          );
        })}
      </div>
    </section>
  );
}
