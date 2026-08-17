"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  AlertTriangle,
  Clock3,
  MapPin,
  Phone,
  RefreshCw,
  ReceiptText,
  Utensils,
} from "lucide-react";
import { ChefOrderActions } from "@/components/chef-order-actions";
import {
  formatChefOrderStatus,
  parseChefOrderResponse,
  type ChefOrder,
} from "@/lib/chef-order-contract";

function money(value: number, currency: string): string {
  try {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency,
      maximumFractionDigits: 2,
    }).format(value);
  } catch {
    return `${currency} ${value.toFixed(2)}`;
  }
}

function itemMetadata(item: ChefOrder["items"][number]): string {
  return [item.category, item.foodType?.replaceAll("_", " ")]
    .filter((value): value is string => Boolean(value))
    .join(" · ");
}

function responseMessage(value: unknown, fallback: string): string {
  return value &&
    typeof value === "object" &&
    "message" in value &&
    typeof value.message === "string"
    ? value.message
    : fallback;
}

function statusTone(status: string): string {
  if (status === "DELIVERED") return "bg-success/10 text-success";
  if (["CHEF_REJECTED", "CANCELLED", "REFUND_FAILED"].includes(status)) {
    return "bg-error/10 text-error";
  }
  if (status.startsWith("REFUND")) return "bg-warning/10 text-warning";
  return "bg-secondary text-contrast-red";
}

export function ChefOrderDetails({ orderId }: { orderId: string }) {
  const [order, setOrder] = useState<ChefOrder | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null);

  const load = useCallback(async (background = false) => {
    if (background) setRefreshing(true);
    else setLoading(true);
    setError("");
    try {
      const response = await fetch(`/api/chef/orders/${encodeURIComponent(orderId)}`, {
        cache: "no-store",
        credentials: "same-origin",
      });
      const body = await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(
          responseMessage(
            body,
            response.status === 404 || response.status === 403
              ? "This order is not available for your approved chef identity."
              : "Chef order is temporarily unavailable.",
          ),
        );
      }
      const parsed = parseChefOrderResponse(body);
      if (!parsed || parsed.id.toLowerCase() !== orderId.toLowerCase()) {
        throw new Error("Craves returned an invalid chef order response.");
      }
      setOrder(parsed);
      setLastUpdatedAt(new Date());
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "Chef order is temporarily unavailable.",
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [orderId]);

  useEffect(() => {
    void load();
  }, [load]);

  if (loading) {
    return (
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_20rem]" aria-hidden="true">
        <div className="space-y-5">
          <div className="h-48 animate-pulse rounded-2xl bg-grey-200" />
          <div className="h-60 animate-pulse rounded-2xl bg-grey-200" />
        </div>
        <div className="h-72 animate-pulse rounded-2xl bg-grey-200" />
        <p className="sr-only" role="status">Loading chef order</p>
      </div>
    );
  }

  if (!order) {
    return (
      <section className="rounded-2xl border border-error/20 bg-white p-8 text-center shadow-[var(--shadow-card)] md:p-12">
        <AlertTriangle className="mx-auto h-10 w-10 text-error" aria-hidden="true" />
        <h2 className="mt-4 font-display text-2xl font-bold text-ink">
          Chef order unavailable
        </h2>
        <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">
          {error}
        </p>
        <div className="mt-6 flex flex-wrap justify-center gap-3">
          <button type="button" onClick={() => void load()} className="btn-primary">
            <RefreshCw className="h-4 w-4" aria-hidden="true" /> Retry
          </button>
          <Link
            href="/chef/orders"
            className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 text-sm font-semibold text-ink hover:border-primary"
          >
            Back to order inbox
          </Link>
        </div>
      </section>
    );
  }

  const address = order.deliveryAddress
    ? [
        order.deliveryAddress.addressLine1,
        order.deliveryAddress.addressLine2,
        order.deliveryAddress.landmark,
        order.deliveryAddress.areaName,
        order.deliveryAddress.city,
        order.deliveryAddress.state,
        order.deliveryAddress.postalCode,
      ]
        .filter(Boolean)
        .join(", ")
    : null;

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
      <div className="space-y-5">
        <section className="rounded-2xl bg-ink p-6 text-white shadow-[var(--shadow-card)] md:p-8">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <span className={`inline-flex rounded-full px-3 py-1 text-xs font-bold ${statusTone(order.status)}`}>
                {formatChefOrderStatus(order.status)}
              </span>
              <h2 className="mt-4 font-display text-3xl font-bold tracking-[-0.04em]">
                {order.kitchenName ?? "Kitchen order"}
              </h2>
              <p className="mt-2 text-sm text-white/65">
                Order #{order.id.slice(-8).toUpperCase()}
              </p>
            </div>
            <div className="text-right">
              <p className="text-xs font-semibold uppercase tracking-[0.08em] text-white/55">
                Food subtotal
              </p>
              <strong className="mt-1 block font-display text-2xl">
                {money(order.foodSubtotal, order.currency)}
              </strong>
            </div>
          </div>
          <div className="mt-5 flex flex-wrap items-center gap-4 text-xs text-white/65">
            <span className="inline-flex items-center gap-1.5">
              <Clock3 className="h-3.5 w-3.5" aria-hidden="true" />
              Placed {new Date(order.createdAt).toLocaleString("en-IN")}
            </span>
            <span>
              {order.prepTimeMinutes
                ? `${order.prepTimeMinutes} minute preparation time`
                : "Preparation time not recorded"}
            </span>
          </div>
          {order.chefResponseNote && (
            <p className="mt-5 rounded-xl border border-white/10 bg-white/5 p-4 text-sm leading-6 text-white/80">
              Chef response note: {order.chefResponseNote}
            </p>
          )}
        </section>

        <ChefOrderActions order={order} onUpdated={setOrder} />

        <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
          <div className="flex items-center gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-secondary text-primary">
              <Utensils className="h-5 w-5" aria-hidden="true" />
            </span>
            <div>
              <p className="craves-overline text-primary">Order contents</p>
              <h2 className="font-display text-xl font-bold text-ink">
                {order.items.reduce((sum, item) => sum + item.quantity, 0)} items
              </h2>
            </div>
          </div>
          <div className="mt-5 divide-y divide-border">
            {order.items.map((item) => {
              const metadata = itemMetadata(item);
              return (
                <article
                  key={item.id}
                  className="flex items-start justify-between gap-4 py-4 first:pt-0 last:pb-0"
                >
                  <div className="min-w-0">
                    <h3 className="font-semibold text-ink">
                      {item.quantity}× {item.itemName}
                    </h3>
                    {metadata && (
                      <p className="mt-1 text-xs text-muted-foreground">
                        {metadata}
                      </p>
                    )}
                    <p className="mt-1 text-xs text-muted-foreground">
                      {money(item.unitPrice, order.currency)} each
                    </p>
                  </div>
                  <strong className="shrink-0 text-ink">
                    {money(item.lineTotal, order.currency)}
                  </strong>
                </article>
              );
            })}
          </div>
        </section>

        {order.deliveryAddress && address && (
          <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
            <div className="flex items-center gap-3">
              <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-secondary text-primary">
                <MapPin className="h-5 w-5" aria-hidden="true" />
              </span>
              <div>
                <p className="craves-overline text-primary">Fulfilment</p>
                <h2 className="font-display text-xl font-bold text-ink">
                  Delivery recipient
                </h2>
              </div>
            </div>
            <p className="mt-5 font-semibold text-ink">
              {order.deliveryAddress.recipientName}
            </p>
            <p className="mt-2 flex items-center gap-2 text-sm text-muted-foreground">
              <Phone className="h-4 w-4" aria-hidden="true" />
              {order.deliveryAddress.contactPhoneNumber}
            </p>
            <p className="mt-3 text-sm leading-6 text-muted-foreground">
              {address}
            </p>
            <p className="mt-3 text-xs leading-5 text-muted-foreground">
              Use these details only for this order’s preparation and fulfilment. Customer data is not retained in this browser.
            </p>
          </section>
        )}
      </div>

      <aside className="space-y-5 lg:sticky lg:top-24">
        <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]">
          <div className="flex items-center gap-3">
            <ReceiptText className="h-5 w-5 text-primary" aria-hidden="true" />
            <h2 className="font-display text-lg font-bold text-ink">
              Backend totals
            </h2>
          </div>
          <dl className="mt-5 space-y-3 text-sm">
            <div className="flex justify-between gap-3">
              <dt className="text-muted-foreground">Food subtotal</dt>
              <dd className="font-semibold text-ink">{money(order.foodSubtotal, order.currency)}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="text-muted-foreground">Platform fee</dt>
              <dd className="font-semibold text-ink">{money(order.platformFee, order.currency)}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="text-muted-foreground">Tax</dt>
              <dd className="font-semibold text-ink">{money(order.taxAmount, order.currency)}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="text-muted-foreground">Delivery fee</dt>
              <dd className="font-semibold text-ink">{money(order.deliveryFee, order.currency)}</dd>
            </div>
            <div className="flex justify-between gap-3 border-t border-border pt-4">
              <dt className="font-display font-bold text-ink">Customer grand total</dt>
              <dd className="font-display text-xl font-bold text-ink">{money(order.grandTotal, order.currency)}</dd>
            </div>
          </dl>
          <p className="mt-4 text-xs leading-5 text-muted-foreground">
            Customer charges are not the chef payout. Use the separate audited earnings ledger for approved net payable values.
          </p>
        </section>

        <button
          type="button"
          disabled={refreshing}
          onClick={() => void load(true)}
          className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg border border-border bg-white px-4 text-sm font-semibold text-ink hover:border-primary disabled:opacity-50"
        >
          <RefreshCw
            className={`h-4 w-4 ${refreshing ? "animate-spin" : ""}`}
            aria-hidden="true"
          />
          Refresh order
        </button>
        <Link href="/chef/orders" className="btn-primary inline-flex w-full">
          Back to order inbox
        </Link>
        {lastUpdatedAt && (
          <p className="text-center text-xs text-muted-foreground">
            Last refreshed {lastUpdatedAt.toLocaleTimeString("en-IN")}
          </p>
        )}
      </aside>

      {error && order && (
        <p role="alert" className="lg:col-span-2 rounded-xl border border-error/20 bg-error/5 p-3 text-sm font-medium text-error">
          {error}
        </p>
      )}
    </div>
  );
}

export default ChefOrderDetails;
