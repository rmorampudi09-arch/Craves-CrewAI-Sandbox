"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ArrowRight, CheckCircle2, ChefHat, Clock3, PackageCheck, RefreshCw, RotateCcw, Truck } from "lucide-react";
import { parseAdminDashboardSummary, type AdminDashboardSummary } from "@/lib/admin-dashboard-contract";

const Visuals = dynamic(() => import("@/components/admin-dashboard-visuals").then(module => module.AdminDashboardVisuals), {
  ssr: false,
  loading: () => <div className="h-[390px] animate-pulse rounded-[28px] bg-white" />
});

const statusLabel = (status: string) => status.toLowerCase().replaceAll("_", " ").replace(/^./, value => value.toUpperCase());

export function AdminDashboard() {
  const [summary, setSummary] = useState<AdminDashboardSummary | null>(null);
  const [message, setMessage] = useState("Loading live operational data…");
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    setRefreshing(true);
    try {
      const response = await fetch("/api/admin/dashboard/summary", { cache: "no-store" });
      const body = await response.json().catch(() => null);
      if (!response.ok) throw new Error(response.status === 403 ? "Administrator access is required." : "Live dashboard data is temporarily unavailable.");
      const parsed = parseAdminDashboardSummary(body);
      if (!parsed) throw new Error("The dashboard received an invalid backend response.");
      setSummary(parsed);
      setMessage("");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Live dashboard data is unavailable.");
    } finally {
      setRefreshing(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  if (!summary) return <section className="rounded-[28px] border border-[#ebe5ef] bg-white p-8 shadow-sm"><div className="h-2 w-24 rounded bg-[#f6b545]" /><h1 className="mt-6 text-3xl font-bold">Operations overview</h1><p className="mt-3 text-[#766981]" role="status">{message}</p><button onClick={() => void load()} className="mt-6 inline-flex items-center gap-2 rounded-xl bg-[#6930ca] px-4 py-3 text-sm font-bold text-white"><RefreshCw size={17} />Try again</button></section>;

  const metrics = summary.metrics;
  const cards = [
    { label: "Orders created", value: metrics.ordersCreated24h, note: "Last 24 hours", icon: PackageCheck, tone: "bg-[#efe8ff] text-[#6930ca]" },
    { label: "Awaiting chef", value: metrics.chefAcceptancePending, note: "Current queue", icon: Clock3, tone: "bg-[#fff3d8] text-[#a86400]" },
    { label: "Preparing", value: metrics.preparing, note: "Current orders", icon: ChefHat, tone: "bg-[#ffe9e2] text-[#bd4b2d]" },
    { label: "Out for delivery", value: metrics.outForDelivery, note: "Current orders", icon: Truck, tone: "bg-[#e5f5ff] text-[#126a9a]" },
    { label: "Delivered", value: metrics.delivered24h, note: "Updated in 24 hours", icon: CheckCircle2, tone: "bg-[#e5f7ec] text-[#24784b]" },
    { label: "Refund attention", value: metrics.refundPending + metrics.refundFailed, note: `${metrics.refundFailed} failed`, icon: RotateCcw, tone: "bg-[#ffe7ea] text-[#a72c3c]" }
  ];

  return <div className="space-y-7">
    <section className="relative overflow-hidden rounded-[32px] bg-[#321c42] px-6 py-8 text-white shadow-[0_28px_75px_-42px_rgba(38,18,55,0.75)] sm:px-9">
      <div className="absolute -right-20 -top-28 h-72 w-72 rounded-full bg-[#6930ca]/40 blur-2xl" /><div className="absolute bottom-0 right-32 h-32 w-32 rounded-full bg-[#f6b545]/20 blur-xl" />
      <div className="relative flex flex-col justify-between gap-6 lg:flex-row lg:items-end"><div><p className="text-xs font-bold uppercase tracking-[0.2em] text-[#f6c868]">Executive operations</p><h1 className="mt-3 text-3xl font-bold sm:text-4xl">Good decisions start with live facts.</h1><p className="mt-3 max-w-2xl text-sm leading-6 text-[#d4c8dc]">Order progress and refund attention are read directly from the owning backend. Commercial values and policy decisions are intentionally excluded.</p></div><button onClick={() => void load()} disabled={refreshing} className="inline-flex w-fit items-center gap-2 rounded-xl bg-white/10 px-4 py-3 text-sm font-bold ring-1 ring-white/15 transition hover:bg-white/15 disabled:opacity-60"><RefreshCw size={17} className={refreshing ? "animate-spin" : ""} />Refresh</button></div>
    </section>

    {message && <div className="rounded-2xl border border-amber-200 bg-amber-50 px-5 py-4 text-sm font-semibold text-amber-900" role="status">{message} Showing the last successfully loaded snapshot.</div>}

    <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-6">
      {cards.map(card => { const Icon = card.icon; return <article key={card.label} className="rounded-[24px] border border-[#ebe5ef] bg-white p-5 shadow-[0_16px_45px_-38px_rgba(58,38,73,0.55)]"><div className={`grid h-11 w-11 place-items-center rounded-2xl ${card.tone}`}><Icon size={21} /></div><p className="mt-5 text-3xl font-bold tabular-nums">{card.value.toLocaleString("en-IN")}</p><p className="mt-1 text-sm font-bold">{card.label}</p><p className="mt-1 text-xs text-[#897b94]">{card.note}</p></article>; })}
    </section>

    <Visuals summary={summary} />

    <section className="grid gap-5 lg:grid-cols-[1fr_1.4fr]">
      <article className="rounded-[28px] border border-[#ebe5ef] bg-white p-6 sm:p-7"><p className="text-xs font-bold uppercase tracking-[0.16em] text-[#8b7b97]">Current flow</p><h2 className="mt-2 text-xl font-bold">Operational stage counts</h2><div className="mt-6 space-y-3">{summary.statusCounts.map(item => <div key={item.status} className="flex items-center justify-between rounded-2xl bg-[#f8f6fa] px-4 py-3"><span className="text-sm font-semibold text-[#62566d]">{statusLabel(item.status)}</span><strong className="rounded-lg bg-white px-3 py-1 text-sm tabular-nums shadow-sm">{item.count.toLocaleString("en-IN")}</strong></div>)}</div></article>
      <article className="rounded-[28px] border border-[#ebe5ef] bg-white p-6 sm:p-7"><p className="text-xs font-bold uppercase tracking-[0.16em] text-[#8b7b97]">Controlled tools</p><h2 className="mt-2 text-xl font-bold">Continue to an admin module</h2><div className="mt-5 grid gap-3 sm:grid-cols-2">{[
        ["Investigate an order or refund", "/admin/operations"], ["Review chef applications", "/admin/chef-reviews"], ["Recover notifications", "/admin/notifications"], ["Secure an account", "/admin/accounts"]
      ].map(([label, href]) => <Link key={href} href={href} className="flex items-center justify-between rounded-2xl border border-[#ede7f1] p-4 text-sm font-bold transition hover:border-[#bca8db] hover:bg-[#faf8fc]"><span>{label}</span><ArrowRight size={17} className="text-[#6930ca]" /></Link>)}</div><p className="mt-5 text-xs leading-5 text-[#8a7c95]">Snapshot generated {new Date(summary.generatedAt).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" })}.</p></article>
    </section>
  </div>;
}
