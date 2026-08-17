"use client";

import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "@tanstack/react-router";
import {
  AlertTriangle,
  LoaderCircle,
  MapPin,
  Plus,
  RefreshCw,
  ShieldCheck,
} from "lucide-react";
import {
  parseCustomerAddresses,
  type CustomerAddress,
} from "@/lib/address-contract";
import { parseCheckout } from "@/lib/checkout-contract";
import { loadSession } from "@/services/auth/cravesAuth";
import {
  cartCurrency,
  cartTotal,
  getCart,
  loadCart,
  validateCart,
  type CartItem,
} from "@/services/api/cravesCart";
import { CheckoutHeader } from "@/components/checkout/CheckoutHeader";
import { CheckoutAddressDialog } from "@/components/checkout/CheckoutAddressDialog";

function money(amount: number, currency = "INR") {
  try {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency,
      maximumFractionDigits: 2,
    }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(2)}`;
  }
}

function checkoutMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : "Checkout could not be prepared. Please try again.";
}

function fullAddress(address: CustomerAddress): string {
  return [
    address.addressLine1,
    address.addressLine2,
    address.landmark,
    address.areaName,
    address.city,
    address.state,
    address.postalCode,
  ]
    .filter(Boolean)
    .join(", ");
}

export default function CheckoutPage() {
  const navigate = useNavigate();
  const [items, setItems] = useState<CartItem[]>([]);
  const [addresses, setAddresses] = useState<CustomerAddress[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [addressDialogOpen, setAddressDialogOpen] = useState(false);
  const [note, setNote] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const prepareCheckout = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const session = await loadSession();
      if (!session) {
        navigate({ to: "/" });
        return;
      }

      await loadCart();
      const nextItems = getCart();
      if (!nextItems.length) {
        navigate({ to: "/cart" });
        return;
      }
      await validateCart();

      const response = await fetch("/api/customer/addresses", {
        cache: "no-store",
        credentials: "same-origin",
      });
      const raw = await response.json().catch(() => null);
      if (!response.ok) {
        const message =
          raw &&
          typeof raw === "object" &&
          "message" in raw &&
          typeof raw.message === "string"
            ? raw.message
            : "Saved addresses could not be loaded.";
        throw new Error(message);
      }
      const parsed = parseCustomerAddresses(raw);
      if (!parsed) throw new Error("Craves returned an invalid address response.");
      const activeAddresses = parsed.filter((address) => address.active);
      const preferred =
        activeAddresses.find((address) => address.isDefault) ?? activeAddresses[0];

      setItems(nextItems);
      setAddresses(activeAddresses);
      setSelectedId(preferred?.id ?? "");
    } catch (caught) {
      setItems([]);
      setAddresses([]);
      setSelectedId("");
      setError(checkoutMessage(caught));
    } finally {
      setLoading(false);
    }
  }, [navigate]);

  useEffect(() => {
    void prepareCheckout();
  }, [prepareCheckout]);

  async function createCheckout() {
    if (!selectedId || busy) return;
    setBusy(true);
    setError("");
    try {
      const response = await fetch("/api/checkout", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          deliveryAddressId: selectedId,
          note: note.trim() || null,
        }),
      });
      const raw = await response.json().catch(() => null);
      if (!response.ok) {
        const message =
          raw &&
          typeof raw === "object" &&
          "message" in raw &&
          typeof raw.message === "string"
            ? raw.message
            : "Checkout could not be created.";
        throw new Error(message);
      }
      const checkout = parseCheckout(raw);
      if (!checkout) throw new Error("Craves returned an invalid checkout response.");
      navigate({
        to: "/checkout/$checkoutId/payment",
        params: { checkoutId: checkout.id },
      });
    } catch (caught) {
      setError(checkoutMessage(caught));
      setBusy(false);
    }
  }

  const subtotal = cartTotal();
  const currency = cartCurrency();
  const selectedAddress = addresses.find((address) => address.id === selectedId);

  return (
    <div className="min-h-screen bg-white pb-32 text-ink">
      <CheckoutHeader
        onBack={() => navigate({ to: "/cart" })}
        title="Delivery and checkout"
        subtitle="Final charges come from the Order Service"
      />

      <main className="mx-auto max-w-5xl px-4 py-6 md:px-6 md:py-8">
        {loading ? (
          <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]" aria-hidden="true">
            <div className="space-y-5">
              <div className="h-64 animate-pulse rounded-2xl bg-grey-200" />
              <div className="h-40 animate-pulse rounded-2xl bg-grey-200" />
            </div>
            <div className="h-72 animate-pulse rounded-2xl bg-grey-200" />
          </div>
        ) : error && items.length === 0 ? (
          <section className="rounded-2xl border border-error/20 bg-white p-8 text-center shadow-[var(--shadow-card)] md:p-12">
            <AlertTriangle className="mx-auto h-10 w-10 text-error" aria-hidden="true" />
            <h1 className="mt-4 font-display text-2xl font-bold text-ink">
              Checkout could not be prepared
            </h1>
            <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">
              {error}
            </p>
            <button type="button" onClick={() => void prepareCheckout()} className="btn-primary mt-6">
              <RefreshCw className="h-4 w-4" aria-hidden="true" /> Retry
            </button>
          </section>
        ) : (
          <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
            <div className="space-y-5">
              <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="craves-overline text-primary">Step 1</p>
                    <h1 className="mt-1 font-display text-2xl font-bold tracking-[-0.035em] text-ink">
                      Delivery address
                    </h1>
                    <p className="mt-2 text-sm leading-6 text-muted-foreground">
                      Only the address selected for this checkout is shown here. Manage addresses to choose another saved address or add a new one.
                    </p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setAddressDialogOpen(true)}
                    className="inline-flex min-h-11 items-center gap-2 rounded-lg border px-4 text-sm"
                  >
                    <Plus className="h-4 w-4" aria-hidden="true" /> Manage address
                  </button>
                </div>

                {!selectedAddress ? (
                  <div className="mt-5 rounded-2xl border border-dashed border-border bg-white p-6 text-center">
                    <MapPin className="mx-auto h-9 w-9 text-primary" aria-hidden="true" />
                    <h2 className="mt-3 font-display text-lg font-bold text-ink">
                      No current delivery address
                    </h2>
                    <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-muted-foreground">
                      Add or select a mapped address before Craves can calculate serviceability and delivery charges.
                    </p>
                    <button
                      type="button"
                      onClick={() => setAddressDialogOpen(true)}
                      className="btn-primary mt-5"
                    >
                      <Plus className="h-4 w-4" aria-hidden="true" /> Add or select address
                    </button>
                  </div>
                ) : (
                  <article className="mt-5 rounded-2xl border border-[#F62E18] bg-white p-5">
                    <div className="flex items-start gap-3">
                      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#C92716] text-black">
                        <MapPin className="h-5 w-5" aria-hidden="true" />
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <h2 className="font-display text-lg font-bold text-ink">
                            {selectedAddress.addressLabel}
                          </h2>
                          {selectedAddress.isDefault && (
                            <span className="rounded-full border border-border bg-white px-2 py-0.5 text-[0.62rem] font-bold uppercase tracking-[0.06em] text-ink">
                              Default
                            </span>
                          )}
                        </div>
                        <p className="mt-2 text-sm font-semibold text-ink">
                          {selectedAddress.recipientName} · {selectedAddress.contactPhoneNumber}
                        </p>
                        <p className="mt-1 text-sm leading-6 text-muted-foreground">
                          {fullAddress(selectedAddress)}
                        </p>
                      </div>
                    </div>
                  </article>
                )}
              </section>

              <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
                <label htmlFor="checkout-note" className="block">
                  <span className="craves-overline text-primary">Optional</span>
                  <span className="mt-1 block font-display text-xl font-bold text-ink">
                    Note for the kitchen
                  </span>
                  <span className="mt-2 block text-sm leading-6 text-muted-foreground">
                    Enter preparation information only. Do not include payment credentials or sensitive personal data.
                  </span>
                </label>
                <textarea
                  id="checkout-note"
                  maxLength={500}
                  value={note}
                  onChange={(event) => setNote(event.target.value)}
                  className="mt-4 min-h-28 w-full resize-y rounded-xl border border-border bg-white p-3 text-base text-ink outline-none placeholder:text-[#9A9A95] focus:border-[#F62E18] focus:ring-0"
                  placeholder="For example: please pack the gravy separately"
                />
                <p className="mt-2 text-right text-xs text-muted-foreground">
                  {note.length}/500
                </p>
              </section>
            </div>

            <aside className="space-y-4 lg:sticky lg:top-24">
              <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]">
                <p className="craves-overline text-primary">Order summary</p>
                <h2 className="mt-1 font-display text-xl font-bold text-ink">
                  {items.reduce((total, item) => total + item.qty, 0)} items
                </h2>
                <ul className="mt-4 divide-y divide-border">
                  {items.map((item) => (
                    <li key={item.id} className="flex gap-3 py-3 text-sm">
                      <span className="min-w-0 flex-1 text-ink">
                        <span className="block truncate font-semibold">{item.name}</span>
                        <span className="text-xs text-muted-foreground">Quantity {item.qty}</span>
                      </span>
                      <span className="shrink-0 font-semibold text-ink">
                        {money(item.lineTotal, item.currency)}
                      </span>
                    </li>
                  ))}
                </ul>
                <div className="mt-3 flex items-center justify-between border-t border-border pt-4">
                  <span className="text-sm text-muted-foreground">Food subtotal</span>
                  <strong className="font-display text-xl text-ink">
                    {money(subtotal, currency)}
                  </strong>
                </div>
                <p className="mt-3 text-xs leading-5 text-muted-foreground">
                  Platform fee, tax, delivery fee and grand total are returned by the Order Service after checkout creation.
                </p>
              </section>

              {selectedAddress && (
                <section className="rounded-2xl border border-border bg-white p-4 text-sm shadow-[var(--shadow-card)]">
                  <p className="font-semibold text-ink">Delivering to {selectedAddress.recipientName}</p>
                  <p className="mt-1 text-xs leading-5 text-muted-foreground">
                    {selectedAddress.areaName}, {selectedAddress.city} {selectedAddress.postalCode}
                  </p>
                </section>
              )}

              <p className="flex items-start gap-2 rounded-xl border border-border bg-white p-4 text-xs leading-5 text-muted-foreground">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-success" aria-hidden="true" />
                Payment details are collected only inside Razorpay hosted checkout. Craves never asks for a card number, CVV or UPI PIN.
              </p>
            </aside>
          </div>
        )}

        {error && items.length > 0 && (
          <p role="alert" className="mt-5 rounded-xl border border-error/20 bg-white p-3 text-sm font-medium text-error">
            {error}
          </p>
        )}
      </main>

      {!loading && items.length > 0 && (
        <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-white/95 p-3 shadow-[0_-8px_32px_rgba(0,0,0,0.08)] backdrop-blur-xl">
          <div className="mx-auto flex max-w-5xl items-center gap-4 px-1 md:px-3">
            <div className="min-w-0">
              <p className="text-[0.68rem] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                Food subtotal
              </p>
              <p className="font-display text-xl font-bold text-ink">
                {money(subtotal, currency)}
              </p>
            </div>
            <button
              type="button"
              disabled={busy || !selectedId}
              onClick={() => void createCheckout()}
              className="btn-primary ml-auto min-h-12 flex-1 sm:flex-none sm:px-8 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {busy && <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />}
              {busy ? "Creating checkout…" : "Continue to secure payment"}
            </button>
          </div>
        </div>
      )}

      <CheckoutAddressDialog
        open={addressDialogOpen}
        selectedId={selectedId}
        onClose={() => setAddressDialogOpen(false)}
        onSelect={setSelectedId}
        onAddressesChange={setAddresses}
      />
    </div>
  );
}
