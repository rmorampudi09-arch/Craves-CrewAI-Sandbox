import Link from "next/link";
import { notFound } from "next/navigation";
import { CustomerOrderDetail } from "@/components/customer-orders";
import { isUuid } from "@/lib/server-api";

export const metadata = { title: "Order details | Craves", robots: { index: false, follow: false } };

export default async function OrderDetailPage({ params }: { params: Promise<{ orderId: string }> }) {
  const { orderId } = await params;
  if (!isUuid(orderId)) notFound();
  return <main className="mx-auto min-h-screen max-w-4xl px-5 py-10"><div className="mb-7 flex items-center justify-between gap-4"><Link className="text-sm font-semibold text-[#F6B545]" href="/orders">← My orders</Link><Link className="rounded-full border border-white/30 px-4 py-2 text-sm text-white" href="/">Home</Link></div><CustomerOrderDetail orderId={orderId} /></main>;
}
