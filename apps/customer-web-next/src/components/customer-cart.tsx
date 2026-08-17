"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import type { CustomerCart } from "@/lib/cart-contract";

function formatMoney(amount: number, currency: string): string {
  try { return new Intl.NumberFormat("en-IN", { style: "currency", currency }).format(amount); }
  catch { return `${currency} ${amount.toFixed(2)}`; }
}

export function CustomerCartView() {
  const [cart, setCart] = useState<CustomerCart | null>(null);
  const [message, setMessage] = useState("Loading your cart…");
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = useCallback(async () => {
    const response = await fetch("/api/cart", { cache: "no-store" });
    const body = await response.json();
    if (!response.ok) throw new Error(body?.message || "Your cart could not be loaded.");
    setCart(body);
    setMessage(body.items.length ? `${body.items.length} cart item${body.items.length === 1 ? "" : "s"}.` : "Your cart is empty.");
  }, []);

  useEffect(() => { load().catch(error => setMessage(error instanceof Error ? error.message : "Your cart could not be loaded.")); }, [load]);

  async function mutate(url: string, method: "PUT" | "DELETE" | "POST", body?: unknown, key = "cart") {
    setBusyId(key);
    try {
      const response = await fetch(url, { method, headers: body ? { "Content-Type": "application/json" } : undefined, body: body ? JSON.stringify(body) : undefined });
      const next = await response.json();
      if (!response.ok) throw new Error(next?.message || "Cart could not be updated.");
      setCart(next);
      setMessage("Cart updated.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Cart could not be updated.");
    } finally {
      setBusyId(null);
    }
  }

  if (!cart) return <section className="rounded-[30px] bg-[#FFF8EC] p-8 text-slate-950"><p role="status">{message}</p></section>;

  return (
    <div className="grid gap-8 lg:grid-cols-[1.15fr_0.85fr]">
      <section className="space-y-4">
        {cart.items.map(item => (
          <article key={item.id} className="rounded-[28px] bg-white p-6 text-slate-950 shadow-xl shadow-black/15">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div><p className="text-xs font-bold uppercase tracking-wider text-[#6930CA]">{item.kitchenName}</p><h2 className="mt-2 text-xl font-bold">{item.itemName}</h2><p className="mt-2 text-sm text-slate-600">{formatMoney(item.unitPrice, item.currency)} each</p></div>
              <strong>{formatMoney(item.lineTotal, item.currency)}</strong>
            </div>
            <div className="mt-5 flex flex-wrap items-center gap-3">
              <label className="text-sm font-semibold">Quantity<input aria-label={`Quantity for ${item.itemName}`} type="number" min={1} max={100} value={item.quantity} disabled={busyId === item.id} onChange={event => void mutate(`/api/cart/items/${item.id}`, "PUT", { quantity: Number(event.target.value) }, item.id)} className="ml-3 w-20 rounded-xl border border-slate-300 px-3 py-2" /></label>
              <button type="button" disabled={busyId === item.id} onClick={() => void mutate(`/api/cart/items/${item.id}`, "DELETE", undefined, item.id)} className="rounded-full border border-red-300 px-4 py-2 text-sm font-bold text-red-700 disabled:opacity-50">Remove</button>
            </div>
          </article>
        ))}
        {!cart.items.length && <div className="rounded-[28px] bg-white p-8 text-center text-slate-600">Discover nearby dishes and add them to your cart.</div>}
      </section>
      <aside className="h-fit rounded-[30px] bg-[#FFF8EC] p-7 text-slate-950 shadow-2xl shadow-black/20">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-[#6930CA]">Cart summary</p>
        <div className="mt-5 flex justify-between text-lg"><span>Food subtotal</span><strong>{formatMoney(cart.foodSubtotal, cart.currency)}</strong></div>
        <p className="mt-4 text-sm leading-6 text-slate-600">Platform fee, tax and delivery fee are not calculated in the browser. Order Service calculates final checkout totals after you select an address.</p>
        <button type="button" disabled={!cart.items.length || busyId !== null} onClick={() => void mutate("/api/cart/validate", "POST")} className="mt-6 w-full rounded-full border border-[#6930CA] px-5 py-3 text-sm font-bold text-[#6930CA] disabled:opacity-50">Validate availability</button>
        <Link href="/checkout" aria-disabled={!cart.items.length} className={`mt-3 flex w-full justify-center rounded-full bg-[#6930CA] px-5 py-3 text-sm font-bold text-white ${!cart.items.length ? "pointer-events-none opacity-50" : ""}`}>Continue to checkout</Link>
        <button type="button" disabled={!cart.items.length || busyId !== null} onClick={() => window.confirm("Clear every item from your cart?") && void mutate("/api/cart", "DELETE")} className="mt-3 w-full rounded-full px-5 py-3 text-sm font-bold text-red-700 disabled:opacity-50">Clear cart</button>
        <p role="status" className="mt-5 text-sm text-slate-600">{message}</p>
      </aside>
    </div>
  );
}
