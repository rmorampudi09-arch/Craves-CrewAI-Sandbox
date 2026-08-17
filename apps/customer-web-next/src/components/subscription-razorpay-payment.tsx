"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { parseCustomerSubscription, type CustomerSubscription } from "@/lib/subscription-contract";
import {
  parseSubscriptionPayment,
  type SubscriptionPayment,
} from "@/lib/subscription-payment-contract";

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayOptions) => { open(): void; on(event: "payment.failed", handler: (response: { error?: { description?: string } }) => void): void };
  }
}

type RazorpaySuccess = { razorpay_payment_id: string; razorpay_order_id: string; razorpay_signature: string };
type RazorpayOptions = {
  key: string; amount: number; currency: string; order_id: string; name: string; description: string;
  handler(response: RazorpaySuccess): void; modal: { ondismiss(): void }; theme: { color: string };
};

const INVOICE_ATTEMPTS = 30;
const INVOICE_WAIT_MS = 3_000;
const PAYMENT_ATTEMPTS = 20;
const PAYMENT_WAIT_MS = 2_000;
const ACTIVATION_ATTEMPTS = 20;
const ACTIVATION_WAIT_MS = 2_000;

function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function money(amount: number, currency: string): string {
  try {
    return new Intl.NumberFormat("en-IN", { style: "currency", currency }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(2)}`;
  }
}

function loadRazorpay(): Promise<void> {
  return new Promise((resolve, reject) => {
    if (window.Razorpay) {
      resolve();
      return;
    }
    const existing = document.querySelector<HTMLScriptElement>('script[data-craves-subscription-razorpay="v1"]');
    if (existing) {
      existing.addEventListener("load", () => resolve(), { once: true });
      existing.addEventListener("error", () => reject(new Error("Razorpay checkout could not be loaded.")), { once: true });
      return;
    }
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.async = true;
    script.dataset.cravesSubscriptionRazorpay = "v1";
    script.referrerPolicy = "strict-origin";
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Razorpay checkout could not be loaded."));
    document.head.appendChild(script);
  });
}

function responseMessage(body: unknown, fallback: string): string {
  return body && typeof body === "object" && "message" in body && typeof body.message === "string"
    ? body.message
    : fallback;
}

async function loadSubscription(subscriptionId: string): Promise<CustomerSubscription> {
  const response = await fetch(`/api/subscriptions/${encodeURIComponent(subscriptionId)}`, {
    cache: "no-store",
    credentials: "same-origin",
  });
  const body = await response.json().catch(() => null);
  if (response.status === 401) throw new Error("Your session expired. Sign in again.");
  if (!response.ok) throw new Error(responseMessage(body, "Meal subscription could not be loaded."));
  const subscription = parseCustomerSubscription(body);
  if (!subscription || subscription.id !== subscriptionId) {
    throw new Error("Craves returned an invalid subscription response.");
  }
  return subscription;
}

async function loadPayment(subscriptionId: string): Promise<SubscriptionPayment | null> {
  const response = await fetch(`/api/subscription-payments/subscriptions/${encodeURIComponent(subscriptionId)}`, {
    cache: "no-store",
    credentials: "same-origin",
  });
  const body = await response.json().catch(() => null);
  if (response.status === 404) return null;
  if (response.status === 401) throw new Error("Your session expired. Sign in again.");
  if (!response.ok) throw new Error(responseMessage(body, "Subscription payment status is temporarily unavailable."));
  const payment = parseSubscriptionPayment(body);
  if (!payment || payment.subscriptionId !== subscriptionId) {
    throw new Error("Craves returned an invalid subscription payment response.");
  }
  return payment;
}

export function SubscriptionRazorpayPayment({ subscriptionId }: { subscriptionId: string }) {
  const [subscription, setSubscription] = useState<CustomerSubscription | null>(null);
  const [payment, setPayment] = useState<SubscriptionPayment | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("Preparing your meal-plan payment…");
  const [error, setError] = useState("");

  const waitForActivation = useCallback(async () => {
    for (let attempt = 0; attempt < ACTIVATION_ATTEMPTS; attempt += 1) {
      const current = await loadSubscription(subscriptionId);
      setSubscription(current);
      if (current.status === "ACTIVE") {
        setMessage("Payment confirmed. Your meal subscription is active.");
        return current;
      }
      if (current.status === "CANCELLED" || current.status === "EXPIRED") {
        return current;
      }
      await sleep(ACTIVATION_WAIT_MS);
    }
    setMessage("Payment is confirmed. Craves is finishing subscription activation; use Refresh if the status has not updated yet.");
    return null;
  }, [subscriptionId]);

  const waitForInvoice = useCallback(async () => {
    setMessage("Your subscription was created. Craves is preparing the first billing invoice…");
    for (let attempt = 0; attempt < INVOICE_ATTEMPTS; attempt += 1) {
      const current = await loadPayment(subscriptionId);
      if (current) {
        setPayment(current);
        setMessage(
          current.status === "PAID"
            ? "Payment is confirmed. Checking subscription activation…"
            : "Your invoice is ready. Continue with Razorpay when you are ready.",
        );
        if (current.status === "PAID") await waitForActivation();
        return current;
      }
      await sleep(INVOICE_WAIT_MS);
    }
    setMessage("The billing invoice is still being prepared. Nothing was charged. Use Refresh invoice to check again.");
    return null;
  }, [subscriptionId, waitForActivation]);

  const bootstrap = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const current = await loadSubscription(subscriptionId);
      setSubscription(current);
      if (current.status === "ACTIVE") {
        setMessage("This meal subscription is already active.");
        return;
      }
      if (current.status === "CANCELLED" || current.status === "EXPIRED") {
        setMessage("This subscription is not payable in its current status.");
        return;
      }
      await waitForInvoice();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Subscription payment could not be prepared.");
    } finally {
      setLoading(false);
    }
  }, [subscriptionId, waitForInvoice]);

  useEffect(() => {
    void bootstrap();
  }, [bootstrap]);

  async function refreshInvoice() {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      const currentSubscription = await loadSubscription(subscriptionId);
      setSubscription(currentSubscription);
      const currentPayment = await loadPayment(subscriptionId);
      setPayment(currentPayment);
      if (!currentPayment) {
        setMessage("The billing invoice is still being prepared. Nothing was charged.");
      } else if (currentPayment.status === "PAID") {
        setMessage("Payment is confirmed. Checking subscription activation…");
        await waitForActivation();
      } else {
        setMessage(`Current payment status: ${currentPayment.status.replaceAll("_", " ").toLowerCase()}.`);
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Subscription payment status could not be refreshed.");
    } finally {
      setBusy(false);
    }
  }

  async function pollPaymentAfterCheckout(): Promise<SubscriptionPayment | null> {
    let latest: SubscriptionPayment | null = null;
    for (let attempt = 0; attempt < PAYMENT_ATTEMPTS; attempt += 1) {
      const current = await loadPayment(subscriptionId);
      if (current) {
        latest = current;
        setPayment(current);
        if (current.status === "PAID") return current;
      }
      await sleep(PAYMENT_WAIT_MS);
    }
    return latest;
  }

  async function openRazorpay() {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      let currentPayment = payment ?? await waitForInvoice();
      if (!currentPayment) {
        throw new Error("The billing invoice is not ready yet. Refresh the invoice and try again.");
      }
      if (currentPayment.status === "PAID") {
        await waitForActivation();
        return;
      }

      const response = await fetch(
        `/api/subscription-payments/invoices/${encodeURIComponent(currentPayment.invoiceId)}/orders`,
        {
          method: "POST",
          credentials: "same-origin",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ subscriptionId }),
        },
      );
      const body = await response.json().catch(() => null);
      if (response.status === 401) throw new Error("Your session expired. Sign in again.");
      if (!response.ok) throw new Error(responseMessage(body, "Razorpay payment order could not be created."));
      const ordered = parseSubscriptionPayment(body);
      if (!ordered || ordered.subscriptionId !== subscriptionId || ordered.invoiceId !== currentPayment.invoiceId) {
        throw new Error("Craves returned an invalid Razorpay subscription payment response.");
      }
      currentPayment = ordered;
      setPayment(ordered);

      if (ordered.status === "PAID") {
        await waitForActivation();
        return;
      }
      if (!ordered.checkoutKeyId || !ordered.providerOrderId) {
        throw new Error("Razorpay did not return a valid checkout session. Nothing was charged.");
      }

      setMessage("Opening secure Razorpay checkout.");
      await loadRazorpay();
      if (!window.Razorpay) throw new Error("Razorpay checkout is unavailable.");
      const result = await new Promise<RazorpaySuccess>((resolve, reject) => {
        const checkout = new window.Razorpay!({
          key: ordered.checkoutKeyId!, amount: Math.round(ordered.amount * 100), currency: ordered.currency,
          order_id: ordered.providerOrderId!, name: "Craves", description: `Meal-plan invoice ${ordered.invoiceId.slice(-8).toUpperCase()}`,
          handler: resolve, modal: { ondismiss: () => reject(new Error("Razorpay checkout was closed before payment confirmation.")) },
          theme: { color: "#6930CA" },
        });
        checkout.on("payment.failed", response => reject(new Error(response.error?.description || "Razorpay payment failed.")));
        checkout.open();
      });
      const verificationResponse = await fetch(`/api/subscription-payments/invoices/${encodeURIComponent(ordered.invoiceId)}/verify`, {
        method: "POST", credentials: "same-origin", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ subscriptionId, providerOrderId: result.razorpay_order_id,
          providerPaymentId: result.razorpay_payment_id, providerSignature: result.razorpay_signature }),
      });
      const verificationBody = await verificationResponse.json().catch(() => null);
      if (!verificationResponse.ok) throw new Error(responseMessage(verificationBody, "Razorpay payment verification failed."));
      const verified = parseSubscriptionPayment(verificationBody);
      if (!verified || verified.invoiceId !== ordered.invoiceId) throw new Error("Craves returned an invalid verification response.");
      setPayment(verified);
      setMessage("Razorpay checkout completed. Confirming subscription activation…");
      const settled = await pollPaymentAfterCheckout();
      if (settled?.status === "PAID") {
        setMessage("Payment confirmed. Activating your meal subscription…");
        await waitForActivation();
      } else if (settled?.status === "FAILED") {
        setMessage("The payment was not completed. You can retry safely; no successful charge was recorded.");
      } else {
        setMessage("Razorpay confirmation is still pending. Use Refresh payment status; do not create a second subscription.");
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Razorpay checkout could not be opened.");
    } finally {
      setBusy(false);
    }
  }

  const active = subscription?.status === "ACTIVE";
  const terminal = subscription?.status === "CANCELLED" || subscription?.status === "EXPIRED";
  const paid = payment?.status === "PAID";
  const canPay = !loading && !active && !terminal && payment !== null && !paid;

  return (
    <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950 shadow-xl shadow-black/10 sm:p-8">
      <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">Meal-plan payment · Razorpay secure checkout</p>
      <h2 className="mt-3 text-3xl font-bold">
        {active ? "Subscription active" : paid ? "Payment confirmed" : "Complete your meal-plan payment"}
      </h2>
      <p className="mt-3 text-sm leading-6 text-slate-600">
        Card, UPI and banking details stay inside Razorpay hosted checkout. Craves verifies every payment server-side.
      </p>

      <dl className="mt-6 grid gap-3 text-sm sm:grid-cols-2">
        <div className="rounded-2xl bg-white p-4">
          <dt className="text-slate-500">Subscription status</dt>
          <dd className="mt-1 font-semibold">{subscription?.status.replaceAll("_", " ") ?? "Loading"}</dd>
        </div>
        <div className="rounded-2xl bg-white p-4">
          <dt className="text-slate-500">Payment status</dt>
          <dd className="mt-1 font-semibold">{payment?.status.replaceAll("_", " ") ?? "Invoice pending"}</dd>
        </div>
        {payment && (
          <>
            <div className="rounded-2xl bg-white p-4">
              <dt className="text-slate-500">Amount</dt>
              <dd className="mt-1 font-semibold">{money(payment.amount, payment.currency)}</dd>
            </div>
            <div className="rounded-2xl bg-white p-4">
              <dt className="text-slate-500">Billing cycle</dt>
              <dd className="mt-1 font-semibold">{payment.cycleStart} → {payment.cycleEnd}</dd>
            </div>
          </>
        )}
      </dl>

      {message && <p role="status" className="mt-5 rounded-2xl bg-white p-4 text-sm leading-6 text-slate-600">{message}</p>}
      {error && <p role="alert" className="mt-5 rounded-2xl border border-red-200 bg-white p-4 text-sm font-semibold text-red-700">{error}</p>}

      <div className="mt-6 flex flex-wrap gap-3">
        {canPay && (
          <button
            type="button"
            disabled={busy}
            onClick={() => void openRazorpay()}
            className="rounded-2xl bg-[#6930CA] px-5 py-3 font-bold text-white disabled:opacity-50"
          >
            {busy ? "Processing…" : payment?.status === "FAILED" ? "Retry with Razorpay" : "Pay with Razorpay"}
          </button>
        )}
        {!active && !terminal && !payment && (
          <button
            type="button"
            disabled={busy || loading}
            onClick={() => void refreshInvoice()}
            className="rounded-2xl border border-[#6930CA] px-5 py-3 font-bold text-[#6930CA] disabled:opacity-50"
          >
            Refresh invoice
          </button>
        )}
        {payment && !active && (
          <button
            type="button"
            disabled={busy}
            onClick={() => void refreshInvoice()}
            className="rounded-2xl border border-[#6930CA] px-5 py-3 font-bold text-[#6930CA] disabled:opacity-50"
          >
            Refresh payment status
          </button>
        )}
        <Link href={`/subscriptions/${subscriptionId}`} className="rounded-2xl border border-slate-300 px-5 py-3 font-bold text-slate-700">
          Subscription details
        </Link>
        <Link href="/subscriptions" className="rounded-2xl border border-slate-300 px-5 py-3 font-bold text-slate-700">
          My meal plans
        </Link>
      </div>
    </section>
  );
}
