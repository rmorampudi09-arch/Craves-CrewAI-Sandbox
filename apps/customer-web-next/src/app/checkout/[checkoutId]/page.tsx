import type { Metadata } from "next";
import Link from "next/link";
import { CheckoutDetails } from "@/components/customer-checkout";

export const metadata: Metadata = { title: "Checkout details | Craves" };

export default async function CheckoutDetailPage({ params }: { params: Promise<{ checkoutId: string }> }) {
  const { checkoutId } = await params;
  return <main className="mx-auto min-h-screen max-w-4xl px-5 py-10 sm:px-8"><Link href="/checkout" className="text-sm font-semibold text-[#F6B545]">← Checkout</Link><div className="mt-8"><CheckoutDetails checkoutId={checkoutId} /></div></main>;
}
