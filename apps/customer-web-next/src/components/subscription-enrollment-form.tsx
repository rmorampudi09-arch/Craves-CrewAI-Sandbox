"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import type { CustomerAddress } from "@/lib/address-contract";
import {
  parseCustomerSubscription,
  type PublicSubscriptionPlan,
} from "@/lib/subscription-contract";

function newIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `subscription:${crypto.randomUUID()}`;
  }
  return `subscription:${Date.now()}:${Math.random().toString(36).slice(2)}`;
}

export function SubscriptionEnrollmentForm({ planId }: { planId: string }) {
  const [plan, setPlan] = useState<PublicSubscriptionPlan | null>(null);
  const [addresses, setAddresses] = useState<CustomerAddress[]>([]);
  const [addressId, setAddressId] = useState("");
  const [startDate, setStartDate] = useState("");
  const [notes, setNotes] = useState("");
  const [message, setMessage] = useState("Loading plan and saved addresses…");
  const [busy, setBusy] = useState(false);
  const idempotencyKey = useRef("");
  const today = useMemo(() => new Date().toISOString().slice(0, 10), []);

  useEffect(() => {
    let active = true;
    Promise.all([
      fetch("/api/subscriptions/plans", { cache: "no-store" }).then(async response => {
        if (!response.ok) throw new Error("Meal plans are temporarily unavailable.");
        return response.json();
      }),
      fetch("/api/customer/addresses", { cache: "no-store" }).then(async response => {
        if (response.status === 401) throw new Error("Your session expired. Sign in again.");
        if (!response.ok) throw new Error("Saved addresses are unavailable.");
        return response.json();
      }),
    ])
      .then(([plansBody, addressesBody]) => {
        if (!active) return;
        const selected = (plansBody as PublicSubscriptionPlan[]).find(item => item.id === planId) ?? null;
        if (!selected) throw new Error("This subscription plan is not active.");
        const activeAddresses = (addressesBody as CustomerAddress[]).filter(item => item.active);
        setPlan(selected);
        setAddresses(activeAddresses);
        const preferred = activeAddresses.find(item => item.isDefault) ?? activeAddresses[0];
        if (preferred) setAddressId(preferred.id);
        setStartDate(today);
        setMessage(activeAddresses.length ? "" : "Create a saved address before subscribing.");
      })
      .catch(error => {
        if (active) {
          setMessage(error instanceof Error ? error.message : "Subscription enrollment is unavailable.");
        }
      });
    return () => {
      active = false;
    };
  }, [planId, today]);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (!plan || !addressId || !startDate || busy) return;
    if (!idempotencyKey.current) idempotencyKey.current = newIdempotencyKey();
    setBusy(true);
    setMessage("");
    try {
      const response = await fetch("/api/subscriptions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": idempotencyKey.current,
        },
        body: JSON.stringify({
          planId: plan.id,
          startDate,
          deliveryAddressId: addressId,
          notes: notes || null,
        }),
      });
      const body = await response.json().catch(() => null);
      if (response.status === 401) throw new Error("Your session expired. Sign in again.");
      if (response.status === 409) {
        throw new Error("This enrollment request conflicts with an earlier submission. Refresh before trying again.");
      }
      if (!response.ok) {
        throw new Error("Subscription could not be created. Check the start date, address and plan status.");
      }
      const subscription = parseCustomerSubscription(body);
      if (!subscription) throw new Error("Craves returned an invalid subscription response.");
      window.location.assign(`/subscriptions/${encodeURIComponent(subscription.id)}/payment`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Subscription could not be created.");
      setBusy(false);
    }
  }

  if (!plan) {
    return (
      <section className="rounded-[28px] bg-[#FFF8EC] p-6 text-slate-950">
        <p role="status">{message}</p>
      </section>
    );
  }

  return (
    <form onSubmit={submit} className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950 sm:p-8">
      <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">{plan.billingPeriod}</p>
      <h2 className="mt-2 text-3xl font-bold">{plan.name}</h2>
      <p className="mt-3 text-sm text-slate-600">{plan.description}</p>
      <label className="mt-6 block text-sm font-bold">
        Saved delivery address
        <select
          value={addressId}
          onChange={event => setAddressId(event.target.value)}
          className="mt-2 min-h-12 w-full rounded-2xl bg-white px-4"
          required
        >
          <option value="">Select address</option>
          {addresses.map(address => (
            <option key={address.id} value={address.id}>
              {address.addressLabel}: {address.addressLine1}, {address.areaName}
            </option>
          ))}
        </select>
      </label>
      <label className="mt-5 block text-sm font-bold">
        Start date
        <input
          type="date"
          min={today}
          value={startDate}
          onChange={event => setStartDate(event.target.value)}
          className="mt-2 min-h-12 w-full rounded-2xl bg-white px-4"
          required
        />
      </label>
      <label className="mt-5 block text-sm font-bold">
        Notes
        <textarea
          maxLength={2000}
          value={notes}
          onChange={event => setNotes(event.target.value)}
          className="mt-2 min-h-24 w-full rounded-2xl bg-white p-4"
        />
      </label>
      <button
        disabled={busy || addresses.length === 0}
        className="mt-6 min-h-12 w-full rounded-2xl bg-[#6930CA] px-5 font-bold text-white disabled:opacity-50"
      >
        {busy ? "Creating…" : "Create subscription & continue to payment"}
      </button>
      {message && (
        <p className="mt-4 text-sm text-slate-600" role="status">
          {message}
        </p>
      )}
    </form>
  );
}
