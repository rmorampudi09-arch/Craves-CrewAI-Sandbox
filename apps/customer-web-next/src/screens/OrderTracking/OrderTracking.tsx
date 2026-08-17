"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { getRouteApi, Link, useNavigate } from "@tanstack/react-router";
import {
  AlertTriangle,
  ExternalLink,
  RefreshCw,
  Satellite,
} from "lucide-react";
import {
  formatOrderStatus,
  parseCustomerOrder,
  type CustomerOrder,
} from "@/lib/order-contract";
import {
  parseDeliveryStatusResponse,
  presentationFor,
  shouldAutoRefresh,
  type DeliveryStatusResponse,
} from "@/lib/delivery-status";
import { loadSession } from "@/services/auth/cravesAuth";
import { TrackingHeader } from "@/components/tracking/TrackingHeader";
import { CurrentStatusCard } from "@/components/tracking/CurrentStatusCard";
import { OrderTimeline } from "@/components/tracking/OrderTimeline";
import { DeliveryAddressCard } from "@/components/tracking/DeliveryAddressCard";
import { TrackingOrderSummaryCard } from "@/components/tracking/TrackingOrderSummaryCard";

const routeApi = getRouteApi("/tracking");
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function responseMessage(value: unknown, fallback: string): string {
  return value &&
    typeof value === "object" &&
    "message" in value &&
    typeof value.message === "string"
    ? value.message
    : fallback;
}

export default function TrackingPage() {
  const navigate = useNavigate();
  const { id } = routeApi.useSearch();
  const [order, setOrder] = useState<CustomerOrder | null>(null);
  const [delivery, setDelivery] = useState<DeliveryStatusResponse | null>(null);
  const deliveryRef = useRef<DeliveryStatusResponse | null>(null);
  const refreshingRef = useRef(false);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null);

  const refresh = useCallback(
    async (orderId: string, background = false) => {
      if (refreshingRef.current) return;
      refreshingRef.current = true;
      if (background) setBusy(true);
      else setLoading(true);
      setError("");
      try {
        const [orderResponse, deliveryResponse] = await Promise.all([
          fetch(`/api/orders/${encodeURIComponent(orderId)}`, {
            cache: "no-store",
            credentials: "same-origin",
          }),
          fetch(`/api/orders/${encodeURIComponent(orderId)}/delivery-status`, {
            cache: "no-store",
            credentials: "same-origin",
          }),
        ]);

        const orderRaw = await orderResponse.json().catch(() => null);
        if (!orderResponse.ok) {
          throw new Error(responseMessage(orderRaw, "Order could not be loaded."));
        }
        const parsedOrder = parseCustomerOrder(orderRaw);
        if (!parsedOrder || parsedOrder.id.toLowerCase() !== orderId.toLowerCase()) {
          throw new Error("Craves returned an invalid order response.");
        }
        setOrder(parsedOrder);

        if (deliveryResponse.ok) {
          const deliveryRaw = await deliveryResponse.json().catch(() => null);
          const parsedDelivery = parseDeliveryStatusResponse(deliveryRaw);
          if (parsedDelivery.orderId.toLowerCase() !== orderId.toLowerCase()) {
            throw new Error("Craves returned delivery tracking for another order.");
          }
          deliveryRef.current = parsedDelivery;
          setDelivery(parsedDelivery);
          setMessage(
            parsedDelivery.status
              ? "Delivery status loaded from the Craves delivery projection."
              : "A delivery job has not been created for this order yet.",
          );
        } else {
          const deliveryRaw = await deliveryResponse.json().catch(() => null);
          deliveryRef.current = null;
          setDelivery(null);
          setMessage(
            deliveryResponse.status === 404
              ? "Delivery tracking will appear when a delivery job is created."
              : responseMessage(
                  deliveryRaw,
                  "Delivery tracking is temporarily unavailable; the order status is still current.",
                ),
          );
        }
        setLastUpdatedAt(new Date());
      } catch (caught) {
        setError(
          caught instanceof Error
            ? caught.message
            : "Order tracking is unavailable.",
        );
      } finally {
        refreshingRef.current = false;
        setLoading(false);
        setBusy(false);
      }
    },
    [],
  );

  useEffect(() => {
    if (!id || !UUID.test(id)) {
      navigate({ to: "/orders", replace: true });
      return;
    }

    let cancelled = false;
    void loadSession().then((session) => {
      if (cancelled) return;
      if (!session) {
        navigate({ to: "/" });
        return;
      }
      void refresh(id);
    });

    const timer = window.setInterval(() => {
      if (
        !cancelled &&
        document.visibilityState === "visible" &&
        shouldAutoRefresh(deliveryRef.current?.status ?? null)
      ) {
        void refresh(id, true);
      }
    }, 30_000);

    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [id, navigate, refresh]);

  if (!id || !UUID.test(id)) return null;

  const deliveryPresentation = presentationFor(delivery?.status ?? null);
  const address = order?.deliveryAddress
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
    : undefined;
  const steps = delivery?.history.length
    ? delivery.history.map((entry) => {
        const item = presentationFor(entry.newStatus);
        return {
          key: `${entry.newStatus}-${entry.recordedAt}`,
          label: item.label,
          desc: new Date(entry.observedAt).toLocaleString("en-IN"),
        };
      })
    : [
        {
          key: order?.status ?? "waiting",
          label: order ? formatOrderStatus(order.status) : "Loading order",
          desc: order
            ? "Current status from the Order Service"
            : "Waiting for the backend response",
        },
      ];

  return (
    <div className="min-h-screen bg-cream pb-20 text-ink">
      <TrackingHeader orderId={id} onBack={() => navigate({ to: "/orders" })} />
      <main className="mx-auto max-w-5xl px-4 py-6 md:px-6 md:py-8">
        {loading ? (
          <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]" aria-hidden="true">
            <div className="space-y-5">
              <div className="h-44 animate-pulse rounded-2xl bg-grey-200" />
              <div className="h-72 animate-pulse rounded-2xl bg-grey-200" />
            </div>
            <div className="h-64 animate-pulse rounded-2xl bg-grey-200" />
          </div>
        ) : !order ? (
          <section className="rounded-2xl border border-error/20 bg-white p-8 text-center shadow-[var(--shadow-card)] md:p-12">
            <AlertTriangle className="mx-auto h-10 w-10 text-error" aria-hidden="true" />
            <h1 className="mt-4 font-display text-2xl font-bold text-ink">
              Tracking unavailable
            </h1>
            <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">
              {error || "The order could not be loaded."}
            </p>
            <div className="mt-6 flex flex-wrap justify-center gap-3">
              <button type="button" onClick={() => void refresh(id)} className="btn-primary">
                <RefreshCw className="h-4 w-4" aria-hidden="true" /> Retry
              </button>
              <Link
                to="/orders"
                className="inline-flex min-h-11 items-center rounded-lg border border-border px-4 text-sm font-semibold text-ink hover:border-primary"
              >
                Back to orders
              </Link>
            </div>
          </section>
        ) : (
          <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem] lg:items-start">
            <div className="space-y-5">
              <CurrentStatusCard
                label={
                  delivery
                    ? deliveryPresentation.label
                    : formatOrderStatus(order.status)
                }
                desc={
                  delivery
                    ? deliveryPresentation.description
                    : "This is the current order status from Craves. Delivery tracking begins after a delivery job is created."
                }
              />

              <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <p className="craves-overline text-primary">Live progress</p>
                    <h2 className="mt-1 font-display text-xl font-bold text-ink">
                      Order and delivery timeline
                    </h2>
                  </div>
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => void refresh(id, true)}
                    className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-border px-4 text-sm font-semibold text-ink hover:border-primary disabled:opacity-50"
                  >
                    <RefreshCw
                      className={`h-4 w-4 ${busy ? "animate-spin" : ""}`}
                      aria-hidden="true"
                    />
                    Refresh
                  </button>
                </div>
                <div className="mt-5">
                  <OrderTimeline steps={steps} currentIndex={steps.length - 1} />
                </div>
              </section>

              {delivery?.trackingUrl && (
                <a
                  href={delivery.trackingUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="flex min-h-12 items-center justify-center gap-2 rounded-xl border border-primary bg-white px-4 text-sm font-bold text-contrast-red shadow-[var(--shadow-card)] hover:bg-secondary"
                >
                  Open delivery-provider tracking
                  <ExternalLink className="h-4 w-4" aria-hidden="true" />
                </a>
              )}

              <DeliveryAddressCard address={address} />
            </div>

            <aside className="space-y-5 lg:sticky lg:top-24">
              <TrackingOrderSummaryCard order={order} />
              <section className="rounded-2xl border border-border bg-white p-4 shadow-[var(--shadow-card)]">
                <p className="flex items-center gap-2 text-sm font-semibold text-ink">
                  <Satellite className="h-4 w-4 text-primary" aria-hidden="true" />
                  Tracking refresh
                </p>
                <p role="status" className="mt-2 text-xs leading-5 text-muted-foreground">
                  {message}
                  {lastUpdatedAt
                    ? ` Last checked ${lastUpdatedAt.toLocaleTimeString("en-IN")}.`
                    : ""}
                </p>
                <p className="mt-2 text-xs leading-5 text-muted-foreground">
                  Active delivery states refresh every 30 seconds while this tab is visible. Terminal states stop polling.
                </p>
              </section>
              <Link to="/home" className="btn-primary inline-flex w-full">
                Back to discovery
              </Link>
            </aside>
          </div>
        )}

        {error && order && (
          <p role="alert" className="mt-5 rounded-xl border border-error/20 bg-white p-3 text-sm font-medium text-error">
            {error}
          </p>
        )}
      </main>
    </div>
  );
}
