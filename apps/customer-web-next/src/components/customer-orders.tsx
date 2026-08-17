"use client";

import { useEffect, useState } from "react";
import type { CustomerOrder } from "@/lib/order-contract";
import { formatOrderStatus } from "@/lib/order-contract";

const money = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 2,
});
const dateTime = new Intl.DateTimeFormat("en-IN", {
  dateStyle: "medium",
  timeStyle: "short",
});

function ErrorCard({ code, retry }: { code: string; retry: () => void }) {
  const expired = code === "SESSION_EXPIRED" || code === "AUTHENTICATION_REQUIRED";

  return (
    <section className="rounded-[28px] bg-[#FFF8EC] p-7 text-slate-950">
      <h2 className="text-2xl font-bold">
        {expired ? "Please sign in again" : "Orders are temporarily unavailable"}
      </h2>
      <p className="mt-3 text-sm text-slate-600">
        {expired
          ? "Your secure session is missing or expired."
          : "Your order data could not be loaded safely."}
      </p>
      <a
        className="mt-6 inline-flex rounded-full bg-[#6930CA] px-5 py-3 font-semibold text-white"
        href={expired ? "/sign-in?returnTo=/orders" : "#"}
        onClick={(event) => {
          if (!expired) {
            event.preventDefault();
            retry();
          }
        }}
      >
        {expired ? "Sign in" : "Try again"}
      </a>
    </section>
  );
}

export function CustomerOrderList() {
  const [orders, setOrders] = useState<CustomerOrder[] | null>(null);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setError("");
    fetch("/api/orders", { cache: "no-store", signal: controller.signal })
      .then(async (response) => {
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body.code ?? `HTTP_${response.status}`);
        return body as CustomerOrder[];
      })
      .then(setOrders)
      .catch((error) => {
        if (error.name !== "AbortError") {
          setError(error.message || "ORDERS_UNAVAILABLE");
        }
      });
    return () => controller.abort();
  }, [reload]);

  if (error) {
    return <ErrorCard code={error} retry={() => setReload((value) => value + 1)} />;
  }
  if (!orders) {
    return (
      <div className="rounded-[28px] bg-[#FFF8EC] p-7 text-slate-600">
        Loading your orders…
      </div>
    );
  }
  if (orders.length === 0) {
    return (
      <section className="rounded-[28px] bg-[#FFF8EC] p-7 text-slate-950">
        <h2 className="text-2xl font-bold">No orders yet</h2>
        <p className="mt-2 text-sm text-slate-600">
          Your chef-specific orders will appear here after checkout.
        </p>
      </section>
    );
  }

  return (
    <div className="space-y-4">
      {orders.map((order) => (
        <article
          key={order.id}
          className="rounded-[28px] bg-[#FFF8EC] p-6 text-slate-950 shadow-xl shadow-black/20"
        >
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">
                {formatOrderStatus(order.status)}
              </p>
              <h2 className="mt-2 text-xl font-bold">{order.kitchenName}</h2>
              <p className="mt-1 text-xs text-slate-500">
                Placed {dateTime.format(new Date(order.createdAt))}
              </p>
            </div>
            <strong className="rounded-full bg-[#F6B545]/25 px-4 py-2">
              {money.format(order.grandTotal)}
            </strong>
          </div>
          <p className="mt-4 text-sm text-slate-600">
            {order.items
              .map((item) => `${item.quantity} × ${item.itemName}`)
              .join(" • ")}
          </p>
          <div className="mt-5 flex flex-wrap gap-3">
            <a
              className="rounded-full bg-[#6930CA] px-5 py-3 text-sm font-semibold text-white"
              href={`/orders/${order.id}`}
            >
              View order
            </a>
            <a
              className="rounded-full border border-[#6930CA] px-5 py-3 text-sm font-semibold text-[#6930CA]"
              href={`/orders/${order.id}/tracking`}
            >
              Track delivery
            </a>
          </div>
        </article>
      ))}
    </div>
  );
}

export function CustomerOrderDetail({ orderId }: { orderId: string }) {
  const [order, setOrder] = useState<CustomerOrder | null>(null);
  const [error, setError] = useState("");
  const [reload, setReload] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setError("");
    fetch(`/api/orders/${encodeURIComponent(orderId)}`, {
      cache: "no-store",
      signal: controller.signal,
    })
      .then(async (response) => {
        const body = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(body.code ?? `HTTP_${response.status}`);
        return body as CustomerOrder;
      })
      .then(setOrder)
      .catch((error) => {
        if (error.name !== "AbortError") {
          setError(error.message || "ORDER_UNAVAILABLE");
        }
      });
    return () => controller.abort();
  }, [orderId, reload]);

  if (error) {
    return <ErrorCard code={error} retry={() => setReload((value) => value + 1)} />;
  }
  if (!order) {
    return (
      <div className="rounded-[28px] bg-[#FFF8EC] p-7 text-slate-600">
        Loading order details…
      </div>
    );
  }

  return (
    <div className="space-y-5 text-slate-950">
      <section className="rounded-[30px] bg-[#FFF8EC] p-7 shadow-xl shadow-black/20">
        <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">
          {formatOrderStatus(order.status)}
        </p>
        <h1 className="mt-2 text-3xl font-bold">{order.kitchenName}</h1>
        <p className="mt-2 text-sm text-slate-600">Order {order.id}</p>
        <a
          className="mt-6 inline-flex rounded-full bg-[#6930CA] px-5 py-3 font-semibold text-white"
          href={`/orders/${order.id}/tracking`}
        >
          Track delivery
        </a>
      </section>

      <section className="rounded-[30px] bg-[#FFF8EC] p-7">
        <h2 className="text-xl font-bold">Items</h2>
        <div className="mt-4 divide-y divide-slate-200">
          {order.items.map((item) => (
            <div key={item.id} className="flex justify-between gap-4 py-4">
              <div>
                <strong>{item.itemName}</strong>
                <p className="text-sm text-slate-500">
                  {item.quantity} × {money.format(item.unitPrice)}
                </p>
              </div>
              <strong>{money.format(item.lineTotal)}</strong>
            </div>
          ))}
        </div>
      </section>

      <section className="grid gap-5 md:grid-cols-2">
        <div className="rounded-[30px] bg-[#FFF8EC] p-7">
          <h2 className="text-xl font-bold">Payment summary</h2>
          <dl className="mt-4 space-y-3 text-sm">
            <div className="flex justify-between">
              <dt>Food subtotal</dt>
              <dd>{money.format(order.foodSubtotal)}</dd>
            </div>
            <div className="flex justify-between">
              <dt>Platform fee</dt>
              <dd>{money.format(order.platformFee)}</dd>
            </div>
            <div className="flex justify-between">
              <dt>Tax</dt>
              <dd>{money.format(order.taxAmount)}</dd>
            </div>
            <div className="flex justify-between">
              <dt>Delivery fee</dt>
              <dd>{money.format(order.deliveryFee)}</dd>
            </div>
            <div className="flex justify-between border-t border-slate-300 pt-3 text-base font-bold">
              <dt>Total</dt>
              <dd>{money.format(order.grandTotal)}</dd>
            </div>
          </dl>
        </div>

        <div className="rounded-[30px] bg-[#FFF8EC] p-7">
          <h2 className="text-xl font-bold">Delivery address</h2>
          {order.deliveryAddress ? (
            <address className="mt-4 not-italic text-sm leading-6 text-slate-600">
              <strong className="text-slate-950">
                {order.deliveryAddress.recipientName}
              </strong>
              <br />
              {order.deliveryAddress.addressLine1}
              <br />
              {order.deliveryAddress.addressLine2 && (
                <>
                  {order.deliveryAddress.addressLine2}
                  <br />
                </>
              )}
              {order.deliveryAddress.landmark && (
                <>
                  {order.deliveryAddress.landmark}
                  <br />
                </>
              )}
              {order.deliveryAddress.areaName && (
                <>
                  {order.deliveryAddress.areaName}
                  <br />
                </>
              )}
              {order.deliveryAddress.city}, {order.deliveryAddress.state}{" "}
              {order.deliveryAddress.postalCode}
            </address>
          ) : (
            <p className="mt-4 text-sm text-slate-500">
              Address snapshot is unavailable for this older order.
            </p>
          )}
        </div>
      </section>
    </div>
  );
}
