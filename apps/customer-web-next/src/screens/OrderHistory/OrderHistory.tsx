"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import {
  AlertTriangle,
  ArrowRight,
  ChevronRight,
  ClipboardList,
  Clock3,
  RefreshCw,
  ReceiptText,
  Utensils,
} from "lucide-react";
import { PersistentCustomerServiceNav } from "@/components/navigation/PersistentCustomerServiceNav";
import {
  formatOrderStatus,
  parseCustomerOrders,
  type CustomerOrder,
} from "@/lib/order-contract";
import { loadSession } from "@/services/auth/cravesAuth";
import { CravesLogo } from "@/components/brand/CravesLogo";

type OrderView = "ACTIVE" | "PAST" | "ALL";

const ACTIVE_STATUSES = new Set([
  "PAYMENT_PENDING",
  "PAID",
  "CHEF_ACCEPTANCE_PENDING",
  "CHEF_ACCEPTED",
  "PREPARING",
  "READY_FOR_PICKUP",
  "OUT_FOR_DELIVERY",
  "REFUND_PENDING",
]);

function money(amount: number, currency: string): string {
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

function OrderSkeleton() {
  return (
    <div className="grid gap-4 md:grid-cols-2" aria-hidden="true">
      {Array.from({ length: 4 }, (_, index) => (
        <div key={index} className="h-56 animate-pulse rounded-2xl bg-grey-200" />
      ))}
    </div>
  );
}

function statusClass(status: string): string {
  if (status === "DELIVERED" || status === "PAID") return "bg-success/10 text-success";
  if (
    status === "CHEF_REJECTED" ||
    status === "CANCELLED" ||
    status === "REFUND_FAILED"
  )
    return "bg-error/10 text-error";
  if (status.startsWith("REFUND")) return "bg-warning/10 text-warning";
  return "bg-secondary text-contrast-red";
}

export default function OrdersPage() {
  const navigate = useNavigate();
  const [orders, setOrders] = useState<CustomerOrder[]>([]);
  const [view, setView] = useState<OrderView>("ACTIVE");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null);

  const load = useCallback(async (background = false) => {
    if (background) setRefreshing(true);
    else setLoading(true);
    setError("");
    try {
      const response = await fetch("/api/orders", {
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
            : "Your orders could not be loaded.";
        throw new Error(message);
      }
      const parsed = parseCustomerOrders(raw);
      if (!parsed) throw new Error("Craves returned an invalid orders response.");
      setOrders(
        [...parsed].sort(
          (left, right) =>
            new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime(),
        ),
      );
      setLastUpdatedAt(new Date());
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "Your orders could not be loaded.",
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    void loadSession().then((session) => {
      if (!active) return;
      if (!session) {
        navigate({ to: "/" });
        return;
      }
      void load();
    });
    return () => {
      active = false;
    };
  }, [load, navigate]);

  const counts = useMemo(
    () => ({
      ACTIVE: orders.filter((order) => ACTIVE_STATUSES.has(order.status)).length,
      PAST: orders.filter((order) => !ACTIVE_STATUSES.has(order.status)).length,
      ALL: orders.length,
    }),
    [orders],
  );

  const visibleOrders = useMemo(() => {
    if (view === "ALL") return orders;
    return orders.filter((order) =>
      view === "ACTIVE"
        ? ACTIVE_STATUSES.has(order.status)
        : !ACTIVE_STATUSES.has(order.status),
    );
  }, [orders, view]);

  return (
    <div className="min-h-screen bg-white pb-20 text-ink">
      <header className="sticky top-0 z-30 border-b border-border bg-white/95 backdrop-blur-xl">
        <div className="mx-auto flex min-h-18 max-w-6xl items-center gap-3 px-4 py-3 md:px-6">
          <Link to="/home" className="flex min-h-11 items-center gap-3 rounded-lg">
            <CravesLogo size="sm" />
            <span>
              <span className="block font-display text-lg font-bold tracking-[-0.03em] text-ink">
                My orders
              </span>
              <span className="block text-xs text-muted-foreground">
                Order and delivery updates
              </span>
            </span>
          </Link>
          <button
            type="button"
            disabled={refreshing || loading}
            onClick={() => void load(true)}
            className="ml-auto flex h-11 w-11 items-center justify-center rounded-lg border border-border bg-white text-ink hover:border-primary disabled:opacity-50"
            aria-label="Refresh orders"
          >
            <RefreshCw
              className={`h-4 w-4 ${refreshing ? "animate-spin" : ""}`}
              aria-hidden="true"
            />
          </button>
        </div>
        <div className="mx-auto max-w-6xl px-4 pb-3 md:px-6">
          <PersistentCustomerServiceNav />
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6 md:px-6 md:py-8">
        <section className="overflow-hidden rounded-2xl bg-ink p-6 text-white shadow-[var(--shadow-card)] md:p-8">
          <p className="craves-overline text-[#F5B400]">Order Service</p>
          <h1 className="mt-2 font-display text-3xl font-bold tracking-[-0.04em] md:text-4xl">
            Follow every kitchen-specific order.
          </h1>
          <p className="mt-3 max-w-2xl text-sm leading-6 text-white/70 md:text-base">
            A single checkout may create separate orders for different kitchens. Each card below shows the backend status and its own delivery tracking.
          </p>
          {lastUpdatedAt && (
            <p className="mt-4 text-xs text-white/55">
              Last refreshed {lastUpdatedAt.toLocaleTimeString("en-IN")}
            </p>
          )}
        </section>

        <nav className="mt-6 flex gap-2 overflow-x-auto pb-1" aria-label="Filter orders">
          {(["ACTIVE", "PAST", "ALL"] as const).map((nextView) => (
            <button
              key={nextView}
              type="button"
              onClick={() => setView(nextView)}
              aria-pressed={view === nextView}
              className={`min-h-11 shrink-0 rounded-full border px-4 text-sm font-semibold transition-colors ${
                view === nextView
                  ? "border-primary bg-primary text-white"
                  : "border-border bg-white text-ink hover:border-primary"
              }`}
            >
              {nextView === "ACTIVE"
                ? "Active"
                : nextView === "PAST"
                  ? "Past"
                  : "All"}{" "}
              ({counts[nextView]})
            </button>
          ))}
        </nav>

        <section className="mt-6" aria-live="polite">
          {loading ? (
            <>
              <OrderSkeleton />
              <p className="sr-only" role="status">Loading your orders</p>
            </>
          ) : error && orders.length === 0 ? (
            <div className="rounded-2xl border border-error/20 bg-white p-8 text-center shadow-[var(--shadow-card)] md:p-12">
              <AlertTriangle className="mx-auto h-10 w-10 text-error" aria-hidden="true" />
              <h2 className="mt-4 font-display text-xl font-bold text-ink">
                Orders unavailable
              </h2>
              <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">
                {error}
              </p>
              <button type="button" onClick={() => void load()} className="btn-primary mt-6">
                <RefreshCw className="h-4 w-4" aria-hidden="true" /> Retry
              </button>
            </div>
          ) : orders.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-border bg-white p-8 text-center shadow-[var(--shadow-card)] md:p-12">
              <ClipboardList className="mx-auto h-12 w-12 text-muted-foreground" aria-hidden="true" />
              <h2 className="mt-4 font-display text-2xl font-bold text-ink">
                No orders yet
              </h2>
              <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-muted-foreground">
                Paid checkouts create backend orders. Your first order will appear here after payment confirmation.
              </p>
              <button type="button" onClick={() => navigate({ to: "/home" })} className="btn-primary mt-6">
                Browse live menu <ArrowRight className="h-4 w-4" aria-hidden="true" />
              </button>
            </div>
          ) : visibleOrders.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-border bg-white p-8 text-center">
              <ReceiptText className="mx-auto h-10 w-10 text-muted-foreground" aria-hidden="true" />
              <h2 className="mt-4 font-display text-xl font-bold text-ink">
                No {view === "ACTIVE" ? "active" : "past"} orders
              </h2>
              <p className="mt-2 text-sm text-muted-foreground">
                Choose another filter to view the remaining backend orders.
              </p>
            </div>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {visibleOrders.map((order) => {
                const headline = order.items[0]?.itemName || order.kitchenName;
                const additional = Math.max(0, order.items.length - 1);
                return (
                  <Link
                    key={order.id}
                    to="/tracking"
                    search={{ id: order.id }}
                    className="group flex h-full flex-col rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] transition hover:-translate-y-1 hover:border-primary/40"
                  >
                    <div className="flex items-start gap-4">
                      <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-secondary text-primary">
                        <Utensils className="h-6 w-6" aria-hidden="true" />
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                          Order #{order.id.slice(-8).toUpperCase()}
                        </p>
                        <h2 className="mt-1 truncate font-display text-lg font-bold text-ink">
                          {headline}
                          {additional > 0 ? ` +${additional} more` : ""}
                        </h2>
                        <p className="mt-1 truncate text-sm text-muted-foreground">
                          {order.kitchenName}
                        </p>
                      </div>
                      <ChevronRight className="h-5 w-5 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-1" aria-hidden="true" />
                    </div>

                    <div className="mt-5 flex flex-wrap items-center gap-2">
                      <span className={`rounded-full px-3 py-1.5 text-xs font-bold ${statusClass(order.status)}`}>
                        {formatOrderStatus(order.status)}
                      </span>
                      <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
                        <Clock3 className="h-3.5 w-3.5" aria-hidden="true" />
                        {new Date(order.createdAt).toLocaleString("en-IN")}
                      </span>
                    </div>

                    <div className="mt-auto flex items-end justify-between gap-4 border-t border-border pt-4">
                      <div>
                        <p className="text-xs text-muted-foreground">
                          {order.items.reduce((total, item) => total + item.quantity, 0)} items
                        </p>
                        <p className="font-display text-xl font-bold text-ink">
                          {money(order.grandTotal, order.currency)}
                        </p>
                      </div>
                      <span className="text-sm font-semibold text-contrast-red">
                        View tracking
                      </span>
                    </div>
                  </Link>
                );
              })}
            </div>
          )}

          {error && orders.length > 0 && (
            <p role="alert" className="mt-5 rounded-xl border border-error/20 bg-white p-3 text-sm font-medium text-error">
              {error}
            </p>
          )}
        </section>
      </main>
    </div>
  );
}
