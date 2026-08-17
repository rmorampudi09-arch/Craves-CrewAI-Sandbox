import type { Metadata } from "next";
import { CustomerCheckoutForm } from "@/components/customer-checkout";

export const metadata: Metadata = { title: "Checkout | Craves" };

export default function CheckoutPage() {
  return <main className="mx-auto min-h-screen max-w-7xl px-5 py-10 sm:px-8"><a href="/cart" className="text-sm font-semibold text-[#F6B545]">← Back to cart</a><p className="mt-8 text-xs font-bold uppercase tracking-[0.25em] text-[#F6B545]">Backend-calculated checkout</p><h1 className="mt-3 text-4xl font-bold text-white sm:text-5xl">Choose where your food should arrive</h1><p className="mt-4 max-w-2xl text-base leading-7 text-slate-300">Craves validates cart availability, snapshots the selected address and calculates every final charge in Order Service.</p><div className="mt-8"><CustomerCheckoutForm /></div></main>;
}
