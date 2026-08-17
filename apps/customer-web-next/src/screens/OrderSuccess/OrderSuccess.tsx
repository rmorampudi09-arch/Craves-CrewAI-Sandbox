"use client";

import { useEffect, useState } from "react";
import { getRouteApi, Link, useNavigate } from "@tanstack/react-router";
import { CheckCircle2, ClipboardList, MapPin } from "lucide-react";
import { PersistentCustomerServiceNav } from "@/components/navigation/PersistentCustomerServiceNav";
import type { CustomerOrder } from "@/lib/order-contract";
import { formatOrderStatus } from "@/lib/order-contract";
import { loadSession } from "@/services/auth/cravesAuth";

const routeApi = getRouteApi("/confirmation");

function money(amount: number, currency: string) {
  try {
    return new Intl.NumberFormat("en-IN", {
      style: "currency",
      currency,
    }).format(amount);
  } catch {
    return `${currency} ${amount}`;
  }
}

export default function ConfirmationPage() {
  const navigate = useNavigate();
  const { id } = routeApi.useSearch();
  const [order, setOrder] = useState<CustomerOrder | null>(null);
  const [message, setMessage] = useState("Loading order…");

  useEffect(() => {
    void (async () => {
      if (!(await loadSession())) {
        navigate({ to: "/" });
        return;
      }
      if (!id || !/^[0-9a-f-]{36}$/i.test(id)) {
        navigate({ to: "/orders" });
        return;
      }
      const response = await fetch(`/api/orders/${id}`, { cache: "no-store" });
      const body = await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(body?.message || "Order could not be loaded.");
      }
      setOrder(body);
    })().catch((error) =>
      setMessage(
        error instanceof Error ? error.message : "Order could not be loaded.",
      ),
    );
  }, [id, navigate]);

  if (!order) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-cream px-4 text-sm text-muted-foreground">
        {message}
      </div>
    );
  }

  const address = order.deliveryAddress
    ? [
        order.deliveryAddress.addressLine1,
        order.deliveryAddress.addressLine2,
        order.deliveryAddress.city,
        order.deliveryAddress.state,
        order.deliveryAddress.postalCode,
      ]
        .filter(Boolean)
        .join(", ")
    : null;

  return (
    <div className="min-h-screen bg-cream">
      <header className="border-b border-border bg-white">
        <div className="mx-auto max-w-5xl px-4 py-3 md:px-6">
          <PersistentCustomerServiceNav />
        </div>
      </header>
      <main className="flex min-h-[calc(100vh-5rem)] items-center justify-center px-4 py-12">
        <div className="w-full max-w-md rounded-3xl border border-border bg-card p-8 text-center shadow-lg">
          <div className="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-primary/10">
            <CheckCircle2 className="h-12 w-12 text-primary" />
          </div>
          <p className="mt-4 font-script text-primary">Order update</p>
          <h1 className="font-display text-3xl font-bold text-ink">
            {formatOrderStatus(order.status)}
          </h1>
          <p className="mt-2 text-sm text-muted-foreground">
            Order from <span className="font-semibold text-ink">{order.kitchenName}</span>.
          </p>
          <div className="mt-6 rounded-2xl border border-border bg-white p-4 text-left">
            <div className="flex justify-between">
              <span className="text-xs uppercase tracking-wider text-muted-foreground">
                Order ID
              </span>
              <span className="font-mono text-sm font-bold text-ink">
                #{order.id.slice(-6).toUpperCase()}
              </span>
            </div>
            <div className="mt-3 flex justify-between">
              <span className="text-xs uppercase tracking-wider text-muted-foreground">
                Backend total
              </span>
              <span className="font-display text-xl font-bold text-primary">
                {money(order.grandTotal, order.currency)}
              </span>
            </div>
            {address && (
              <p className="mt-3 flex items-start gap-2 border-t border-border pt-3 text-xs text-ink/80">
                <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0 text-primary" />
                {address}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={() =>
              navigate({ to: "/tracking", search: { id: order.id } })
            }
            className="btn-primary mt-6 flex w-full justify-center"
          >
            <ClipboardList className="h-4 w-4" /> Track your order
          </button>
          <Link to="/orders" className="mt-3 block text-sm font-semibold text-primary">
            View all orders
          </Link>
        </div>
      </main>
    </div>
  );
}
