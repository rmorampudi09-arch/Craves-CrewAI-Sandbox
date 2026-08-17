"use client";

import { FormEvent, useEffect, useState } from "react";
import Link from "next/link";
import type { CustomerAddress } from "@/lib/address-contract";
import type { CustomerCart } from "@/lib/cart-contract";
import type { CustomerCheckout } from "@/lib/checkout-contract";

function formatMoney(amount: number, currency: string): string {
  try { return new Intl.NumberFormat("en-IN", { style: "currency", currency }).format(amount); }
  catch { return `${currency} ${amount.toFixed(2)}`; }
}

export function CustomerCheckoutForm() {
  const [cart, setCart] = useState<CustomerCart | null>(null);
  const [addresses, setAddresses] = useState<CustomerAddress[]>([]);
  const [deliveryAddressId, setDeliveryAddressId] = useState("");
  const [note, setNote] = useState("");
  const [checkout, setCheckout] = useState<CustomerCheckout | null>(null);
  const [message, setMessage] = useState("Loading cart and addresses…");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    Promise.all([
      fetch("/api/cart", { cache: "no-store" }).then(async response => { const body = await response.json(); if (!response.ok) throw new Error(body?.message || "Cart could not be loaded."); return body; }),
      fetch("/api/customer/addresses", { cache: "no-store" }).then(async response => { const body = await response.json(); if (!response.ok) throw new Error(body?.message || "Addresses could not be loaded."); return body; })
    ]).then(([nextCart, nextAddresses]) => {
      setCart(nextCart);
      setAddresses(nextAddresses);
      const preferred = nextAddresses.find((address: CustomerAddress) => address.isDefault) ?? nextAddresses[0];
      setDeliveryAddressId(preferred?.id ?? "");
      setMessage(!nextCart.items.length ? "Your cart is empty." : !nextAddresses.length ? "Add a saved address before checkout." : "Review and create checkout.");
    }).catch(error => setMessage(error instanceof Error ? error.message : "Checkout information could not be loaded."));
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!cart?.items.length || !deliveryAddressId) return;
    setBusy(true); setCheckout(null); setMessage("Validating cart and creating checkout…");
    try {
      const validation = await fetch("/api/cart/validate", { method: "POST" });
      const validated = await validation.json();
      if (!validation.ok) throw new Error(validated?.message || "Cart validation failed.");
      const response = await fetch("/api/checkout", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ deliveryAddressId, note: note.trim() || null }) });
      const body = await response.json();
      if (!response.ok) throw new Error(body?.message || "Checkout could not be created.");
      setCheckout(body);
      setMessage("Checkout created. Review the backend-calculated totals before payment.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Checkout could not be created.");
    } finally { setBusy(false); }
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_0.9fr]">
      <form onSubmit={submit} className="rounded-[30px] bg-[#FFF8EC] p-7 text-slate-950 shadow-2xl shadow-black/20">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-[#6930CA]">Delivery details</p>
        <label className="mt-5 block text-sm font-semibold">Saved delivery address<select required value={deliveryAddressId} onChange={event => setDeliveryAddressId(event.target.value)} className="mt-2 w-full rounded-2xl border border-slate-300 bg-white px-4 py-3"><option value="">Choose an address</option>{addresses.map(address => <option key={address.id} value={address.id}>{address.addressLabel}: {address.addressLine1}, {address.areaName}</option>)}</select></label>
        <Link href="/addresses" className="mt-2 inline-flex text-sm font-semibold text-[#6930CA]">Manage saved addresses</Link>
        <label className="mt-5 block text-sm font-semibold">Order note<textarea maxLength={500} rows={4} value={note} onChange={event => setNote(event.target.value)} className="mt-2 w-full rounded-2xl border border-slate-300 bg-white px-4 py-3" placeholder="Optional instructions for the chef" /></label>
        <button disabled={busy || !cart?.items.length || !deliveryAddressId} className="mt-6 w-full rounded-full bg-[#6930CA] px-6 py-3 text-sm font-bold text-white disabled:opacity-50">{busy ? "Creating checkout…" : "Create checkout"}</button>
        <p role="status" className="mt-4 text-sm text-slate-600">{message}</p>
      </form>
      <section className="h-fit rounded-[30px] bg-white p-7 text-slate-950 shadow-2xl shadow-black/15">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-[#6930CA]">{checkout ? "Checkout totals" : "Cart preview"}</p>
        {!checkout && cart && <><div className="mt-5 space-y-3">{cart.items.map(item => <div key={item.id} className="flex justify-between gap-4 text-sm"><span>{item.quantity} × {item.itemName}</span><strong>{formatMoney(item.lineTotal, item.currency)}</strong></div>)}</div><div className="mt-5 flex justify-between border-t border-slate-200 pt-4"><span>Food subtotal</span><strong>{formatMoney(cart.foodSubtotal, cart.currency)}</strong></div><p className="mt-4 text-sm leading-6 text-slate-600">Final platform fee, tax and delivery fee come only from Order Service checkout.</p></>}
        {checkout && <><dl className="mt-5 space-y-3 text-sm">{[["Food subtotal", checkout.foodSubtotal], ["Platform fee", checkout.platformFee], ["Tax", checkout.taxAmount], ["Delivery fee", checkout.deliveryFee]].map(([label, amount]) => <div key={String(label)} className="flex justify-between"><dt>{label}</dt><dd>{formatMoney(Number(amount), checkout.currency)}</dd></div>)}<div className="flex justify-between border-t border-slate-200 pt-4 text-lg font-bold"><dt>Grand total</dt><dd>{formatMoney(checkout.grandTotal, checkout.currency)}</dd></div></dl><p className="mt-4 text-sm text-slate-600">Checkout reference: {checkout.id}</p><Link href={`/checkout/${checkout.id}/payment`} className="mt-6 flex w-full justify-center rounded-full bg-[#6930CA] px-6 py-3 text-sm font-bold text-white">Continue to payment</Link><Link href={`/checkout/${checkout.id}`} className="mt-3 flex justify-center text-sm font-semibold text-[#6930CA]">View checkout details</Link></>}
      </section>
    </div>
  );
}

export function CheckoutDetails({ checkoutId }: { checkoutId: string }) {
  const [checkout, setCheckout] = useState<CustomerCheckout | null>(null);
  const [message, setMessage] = useState("Loading checkout…");
  useEffect(() => { fetch(`/api/checkout/${checkoutId}`, { cache: "no-store" }).then(async response => { const body = await response.json(); if (!response.ok) throw new Error(body?.message || "Checkout could not be loaded."); return body; }).then(value => { setCheckout(value); setMessage(""); }).catch(error => setMessage(error instanceof Error ? error.message : "Checkout could not be loaded.")); }, [checkoutId]);
  if (!checkout) return <section className="rounded-[30px] bg-[#FFF8EC] p-8 text-slate-950"><p>{message}</p></section>;
  return <section className="rounded-[30px] bg-[#FFF8EC] p-7 text-slate-950 shadow-2xl shadow-black/20"><p className="text-xs font-bold uppercase tracking-[0.2em] text-[#6930CA]">{checkout.status.replace("_", " ")}</p><h1 className="mt-3 text-3xl font-bold">Checkout {checkout.id.slice(0, 8)}</h1><p className="mt-3 text-sm text-slate-600">{checkout.orders.length} chef-specific order{checkout.orders.length === 1 ? "" : "s"}</p><dl className="mt-6 space-y-3">{[["Food subtotal", checkout.foodSubtotal], ["Platform fee", checkout.platformFee], ["Tax", checkout.taxAmount], ["Delivery fee", checkout.deliveryFee], ["Grand total", checkout.grandTotal]].map(([label, amount]) => <div key={String(label)} className="flex justify-between border-b border-slate-200 pb-3"><dt>{label}</dt><dd className="font-bold">{formatMoney(Number(amount), checkout.currency)}</dd></div>)}</dl><div className="mt-6 flex flex-wrap gap-3"><Link href={`/checkout/${checkout.id}/payment`} className="rounded-full bg-[#6930CA] px-5 py-3 text-sm font-bold text-white">Continue to payment</Link><Link href="/orders" className="rounded-full border border-[#6930CA] px-5 py-3 text-sm font-bold text-[#6930CA]">My orders</Link></div></section>;
}
