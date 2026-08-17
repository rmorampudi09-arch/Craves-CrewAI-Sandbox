"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  ChefHat,
  ChevronRight,
  Clock3,
  RefreshCw,
} from "lucide-react";
import {
  formatChefOrderStatus,
  parseChefOrdersResponse,
  type ChefOrder,
} from "@/lib/chef-order-contract";

type InboxView = "ACTION_REQUIRED" | "IN_PROGRESS" | "COMPLETED" | "ALL";

const ACTION_REQUIRED = new Set(["CHEF_ACCEPTANCE_PENDING"]);
const IN_PROGRESS = new Set([
  "CHEF_ACCEPTED",
  "PREPARING",
  "READY_FOR_PICKUP",
  "OUT_FOR_DELIVERY",
]);
const COMPLETED = new Set([
  "DELIVERED",
  "CHEF_REJECTED",
  "CANCELLED",
  "REFUNDED",
  "REFUND_FAILED",
]);

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

function statusTone(status: string): string {
  if (status === "DELIVERED") return "bg-success/10 text-success";
  if (["CHEF_REJECTED", "CANCELLED", "REFUND_FAILED"].includes(status)) {
    return "bg-error/10 text-error";
  }
  if (status.startsWith("REFUND")) return "bg-warning/10 text-warning";
  return "bg-secondary text-contrast-red";
}

export function ChefOrderInbox() {
  const [orders, setOrders] = useState<ChefOrder[]>([]);
  const [view, setView] = useState<InboxView>("ACTION_REQUIRED");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null);

  const load = useCallback(async (background = false) => {
    if (background) setRefreshing(true);
    else setLoading(true);
    setError("");
    try {
      const response = await fetch("/api/chef/orders", {
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
            : response.status === 403
              ? "An approved chef role is required."
              : "Chef orders are temporarily unavailable.";
        throw new Error(message);
      }
      const parsed = parseChefOrdersResponse(raw);
      if (!parsed) throw new Error("Craves returned an invalid chef orders response.");
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
          : "Chef orders are temporarily unavailable.",
      );
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const counts = useMemo(
    () => ({
      ACTION_REQUIRED: orders.filter((order) => ACTION_REQUIRED.has(order.status))
        .length,
      IN_PROGRESS: orders.filter((order) => IN_PROGRESS.has(order.status)).length,
      COMPLETED: orders.filter((order) => COMPLETED.has(order.status)).length,
      ALL: orders.length,
    }),
    [orders],
  );

  const visibleOrders = useMemo(() => {
    if (view === "ALL") return orders;
    const statuses =
      view === "ACTION_REQUIRED"
        ? ACTION_REQUIRED
        : view === "IN_PROGRESS"
          ? IN_PROGRESS
          : COMPLETED;
    return orders.filter((order) => statuses.has(order.status));
  }, [orders, view]);

  return (
    <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="craves-overline text-primary">Order Service</p>
          <h2 className="mt-1 font-display text-2xl font-bold tracking-[-0.035em] text-ink">
            Kitchen order inbox
          </h2>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">
            Only orders owned by your approved kitchen are returned. Acceptance, rejection and ready-for-pickup actions remain status-controlled and idempotent on the backend.
          </p>
        </div>
        <button
          type="button"
          disabled={refreshing || loading}
          onClick={() => void load(true)}
          className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-border px-4 text-sm font-semibold text-ink hover:border-primary disabled:opacity-50"
        >
          <RefreshCw
            className={`h-4 w-4 ${refreshing ? "animate-spin" : ""}`}
            aria-hidden="true"
          />
          Refresh
        </button>
      </div>

      <nav className="mt-5 flex gap-2 overflow-x-auto pb-1" aria-label="Filter chef orders">
        {(["ACTION_REQUIRED", "IN_PROGRESS", "COMPLETED", "ALL"] as const).map(
          (nextView) => (
            <button
              key={nextView}
              type="button"
              onClick={() => setView(nextView)}
              aria-pressed={view === nextView}
              className={`min-h-11 shrink-0 rounded-full border px-4 text-sm font-semibold ${
                view === nextView
                  ? "border-primary bg-primary text-white"
                  : "border-border bg-white text-ink hover:border-primary"
              }`}
            >
              {nextView === "ACTION_REQUIRED"
                ? "Action required"
                : nextView === "IN_PROGRESS"
                  ? "In progress"
                  : nextView === "COMPLETED"
                    ? "Completed"
                    : "All"}{" "}
              ({counts[nextView]})
            </button>
          ),
        )}
      </nav>

      {loading ? (
        <div className="mt-6 grid gap-4 md:grid-cols-2" aria-hidden="true">
          {Array.from({ length: 4 }, (_, index) => (
            <div key={index} className="h-52 animate-pulse rounded-2xl bg-grey-200" />
          ))}
          <p className="sr-only" role="status">Loading chef orders</p>
        </div>
      ) : error && orders.length === 0 ? (
        <div className="mt-6 rounded-2xl border border-error/20 bg-error/5 p-8 text-center">
          <AlertTriangle className="mx-auto h-10 w-10 text-error" aria-hidden="true" />
          <h3 className="mt-4 font-display text-xl font-bold text-ink">
            Order inbox unavailable
          </h3>
          <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">
            {error}
          </p>
          <button type="button" onClick={() => void load()} className="btn-primary mt-6">
            <RefreshCw className="h-4 w-4" aria-hidden="true" /> Retry
          </button>
        </div>
      ) : orders.length === 0 ? (
        <div className="mt-6 rounded-2xl border border-dashed border-border bg-cream p-8 text-center">
          <ChefHat className="mx-auto h-10 w-10 text-muted-foreground" aria-hidden="true" />
          <h3 className="mt-4 font-display text-xl font-bold text-ink">
            No chef-owned orders yet
          </h3>
          <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">
            Paid customer checkouts create kitchen-specific orders. New orders will appear here when they belong to your kitchen.
          </p>
        </div>
      ) : visibleOrders.length === 0 ? (
        <div className="mt-6 rounded-2xl border border-dashed border-border bg-cream p-8 text-center">
          <CheckCircle2 className="mx-auto h-10 w-10 text-muted-foreground" aria-hidden="true" />
          <h3 className="mt-4 font-display text-xl font-bold text-ink">
            No orders in this view
          </h3>
          <p className="mt-2 text-sm text-muted-foreground">
            Choose another filter to review the remaining backend orders.
          </p>
        </div>
      ) : (
        <div className="mt-6 grid gap-4 md:grid-cols-2">
          {visibleOrders.map((order) => {
            const headline = order.items[0]?.itemName ?? "Kitchen order";
            const additional = Math.max(0, order.items.length - 1);
            return (
              <Link
                key={order.id}
                href={`/chef/orders/${order.id}`}
                className="group flex h-full flex-col rounded-2xl border border-border bg-cream p-5 transition hover:-translate-y-1 hover:border-primary/40"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <span className={`inline-flex rounded-full px-3 py-1 text-xs font-bold ${statusTone(order.status)}`}>
                      {formatChefOrderStatus(order.status)}
                    </span>
                    <h3 className="mt-3 truncate font-display text-lg font-bold text-ink">
                      {headline}
                      {additional > 0 ? ` +${additional} more` : ""}
                    </h3>
                    <p className="mt-1 text-xs text-muted-foreground">
                      Order #{order.id.slice(-8).toUpperCase()}
                    </p>
                  </div>
                  <ChevronRight className="h-5 w-5 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-1" aria-hidden="true" />
                </div>
                <div className="mt-4 flex flex-wrap gap-3 text-xs text-muted-foreground">
                  <span className="inline-flex items-center gap-1.5">
                    <Clock3 className="h-3.5 w-3.5" aria-hidden="true" />
                    {new Date(order.createdAt).toLocaleString("en-IN")}
                  </span>
                  <span>
                    {order.prepTimeMinutes
                      ? `${order.prepTimeMinutes} min prep`
                      : "Prep time pending"}
                  </span>
                </div>
                <div className="mt-auto flex items-end justify-between gap-4 border-t border-border pt-4">
                  <span className="text-xs text-muted-foreground">
                    {order.items.reduce((sum, item) => sum + item.quantity, 0)} items
                  </span>
                  <strong className="font-display text-xl text-ink">
                    {money(order.foodSubtotal, order.currency)}
                  </strong>
                </div>
              </Link>
            );
          })}
        </div>
      )}

      {lastUpdatedAt && (
        <p className="mt-5 text-xs text-muted-foreground">
          Last refreshed {lastUpdatedAt.toLocaleString("en-IN")}
        </p>
      )}
      {error && orders.length > 0 && (
        <p role="alert" className="mt-4 rounded-xl border border-error/20 bg-error/5 p-3 text-sm font-medium text-error">
          {error}
        </p>
      )}
    </section>
  );
}

export default ChefOrderInbox;
