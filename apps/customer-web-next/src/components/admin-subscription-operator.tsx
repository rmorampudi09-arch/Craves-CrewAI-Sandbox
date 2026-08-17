"use client";

import { useCallback, useEffect, useState } from "react";
import type {
  AdminSubscriptionHistory,
  AdminSubscriptionPage,
  AdminSubscriptionStatus,
  AdminSubscriptionSummary,
} from "@/lib/admin-subscription-operation-contract";

const STATUSES: AdminSubscriptionStatus[] = [
  "PENDING_PAYMENT", "ACTIVE", "PAUSED", "PAYMENT_FAILED", "EXPIRED", "CANCELLED",
];

function short(value: string | null): string {
  if (!value) return "—";
  return value.length > 14 ? `${value.slice(0, 8)}…${value.slice(-4)}` : value;
}

export function AdminSubscriptionOperator() {
  const [items, setItems] = useState<AdminSubscriptionSummary[]>([]);
  const [statusFilter, setStatusFilter] = useState("");
  const [planFilter, setPlanFilter] = useState("");
  const [cursor, setCursor] = useState<{ createdAt: string; id: string } | null>(null);
  const [hasMore, setHasMore] = useState(false);
  const [selected, setSelected] = useState<AdminSubscriptionSummary | null>(null);
  const [history, setHistory] = useState<AdminSubscriptionHistory[]>([]);
  const [nextStatus, setNextStatus] = useState<AdminSubscriptionStatus>("ACTIVE");
  const [reason, setReason] = useState("");
  const [message, setMessage] = useState("Loading subscriptions…");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async (append: boolean, overrideCursor?: { createdAt: string; id: string } | null) => {
    const activeCursor = overrideCursor === undefined ? cursor : overrideCursor;
    const query = new URLSearchParams({ limit: "50" });
    if (statusFilter) query.set("status", statusFilter);
    if (planFilter.trim()) query.set("planId", planFilter.trim());
    if (append && activeCursor) {
      query.set("afterCreatedAt", activeCursor.createdAt);
      query.set("afterId", activeCursor.id);
    }
    const response = await fetch(`/api/admin/subscriptions?${query.toString()}`, { cache: "no-store" });
    const body = await response.json().catch(() => null);
    if (response.status === 401) throw new Error("Administrator session expired.");
    if (response.status === 403) throw new Error("Subscription operations access is required.");
    if (response.status === 400) throw new Error("Check the plan UUID or status filter.");
    if (!response.ok) throw new Error("Subscription operations are temporarily unavailable.");
    const page = body as AdminSubscriptionPage;
    setItems(current => append ? [...current, ...page.items] : page.items);
    setCursor(page.hasMore && page.nextCreatedAt && page.nextId ? { createdAt: page.nextCreatedAt, id: page.nextId } : null);
    setHasMore(page.hasMore);
    setMessage(page.items.length || append ? "" : "No subscriptions match these filters.");
  }, [cursor, planFilter, statusFilter]);

  useEffect(() => {
    void load(false, null).catch(error => setMessage(error instanceof Error ? error.message : "Subscription operations are unavailable."));
    // Initial load only; filters are applied explicitly so typing a plan UUID does not issue partial queries.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function applyFilters() {
    setBusy(true); setMessage("Loading subscriptions…"); setSelected(null); setHistory([]); setCursor(null);
    try { await load(false, null); }
    catch (error) { setMessage(error instanceof Error ? error.message : "Subscription operations are unavailable."); }
    finally { setBusy(false); }
  }

  async function selectSubscription(subscription: AdminSubscriptionSummary) {
    setSelected(subscription); setNextStatus(subscription.status); setReason(""); setMessage("");
    const response = await fetch(`/api/admin/subscriptions/${subscription.id}/history?limit=100`, { cache: "no-store" });
    if (response.ok) setHistory(await response.json() as AdminSubscriptionHistory[]); else setHistory([]);
  }

  async function applyStatus() {
    if (!selected || !reason.trim()) { setMessage("Select a subscription and enter an operational reason."); return; }
    if (!window.confirm(`Change subscription ${selected.id} from ${selected.status} to ${nextStatus}? This action is audited.`)) return;
    setBusy(true); setMessage("");
    try {
      const response = await fetch(`/api/admin/subscriptions/${selected.id}/status`, {
        method: "PATCH", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status: nextStatus, reason: reason.trim() }),
      });
      if (!response.ok) throw new Error("Subscription status could not be updated.");
      setSelected(current => current ? { ...current, status: nextStatus, updatedAt: new Date().toISOString() } : current);
      setItems(current => current.map(item => item.id === selected.id ? { ...item, status: nextStatus, updatedAt: new Date().toISOString() } : item));
      setReason("");
      const historyResponse = await fetch(`/api/admin/subscriptions/${selected.id}/history?limit=100`, { cache: "no-store" });
      if (historyResponse.ok) setHistory(await historyResponse.json() as AdminSubscriptionHistory[]);
      setMessage("Subscription status updated and audited.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Subscription status update failed."); }
    finally { setBusy(false); }
  }

  return <div className="space-y-7">
    <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950 sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-4"><div><h2 className="text-2xl font-bold">Subscription operations</h2><p className="mt-2 text-sm text-slate-600">Keyset pagination keeps this workspace usable as subscription volume grows. Filter by state or exact plan UUID.</p></div><button disabled={busy} onClick={() => void applyFilters()} className="rounded-2xl border border-[#6930CA] px-4 py-2 font-bold text-[#6930CA] disabled:opacity-50">Refresh</button></div>
      <div className="mt-5 grid gap-3 md:grid-cols-[.8fr_1.2fr_auto]"><select value={statusFilter} onChange={event => setStatusFilter(event.target.value)} className="min-h-12 rounded-2xl bg-white px-4"><option value="">All statuses</option>{STATUSES.map(status => <option key={status} value={status}>{status}</option>)}</select><input value={planFilter} onChange={event => setPlanFilter(event.target.value)} placeholder="Optional plan UUID" className="min-h-12 rounded-2xl bg-white px-4" /><button disabled={busy} onClick={() => void applyFilters()} className="rounded-2xl bg-[#6930CA] px-5 py-3 font-bold text-white disabled:opacity-50">Apply filters</button></div>
      {message && <p role="status" className="mt-4 rounded-2xl bg-white p-4 text-sm text-slate-700">{message}</p>}
      <div className="mt-5 overflow-x-auto rounded-2xl bg-white"><table className="min-w-[900px] w-full text-left text-sm"><thead><tr className="border-b"><th className="p-3">Created</th><th className="p-3">Status</th><th className="p-3">Subscription</th><th className="p-3">Customer</th><th className="p-3">Plan</th><th className="p-3">Next meal</th><th className="p-3">Action</th></tr></thead><tbody>{items.map(item => <tr key={item.id} className="border-b last:border-b-0"><td className="p-3">{new Date(item.createdAt).toLocaleString("en-IN")}</td><td className="p-3 font-bold">{item.status}</td><td className="p-3 font-mono text-xs" title={item.id}>{short(item.id)}</td><td className="p-3 font-mono text-xs" title={item.customerIdentityId}>{short(item.customerIdentityId)}</td><td className="p-3 font-mono text-xs" title={item.planId}>{short(item.planId)}</td><td className="p-3">{item.nextServiceDate ?? "—"}</td><td className="p-3"><button onClick={() => void selectSubscription(item)} className="rounded-xl border border-[#6930CA] px-3 py-2 font-bold text-[#6930CA]">Review</button></td></tr>)}</tbody></table></div>
      {hasMore && <button disabled={busy || !cursor} onClick={() => void load(true).catch(error => setMessage(error instanceof Error ? error.message : "More subscriptions could not be loaded."))} className="mt-4 rounded-2xl border border-[#6930CA] px-5 py-3 font-bold text-[#6930CA] disabled:opacity-50">Load more</button>}
    </section>

    {selected && <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950 sm:p-8">
      <div><p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">Selected subscription</p><h2 className="mt-2 break-all text-xl font-bold">{selected.id}</h2></div>
      <dl className="mt-5 grid gap-3 text-sm md:grid-cols-3"><div><dt className="text-slate-500">Customer identity</dt><dd className="break-all font-mono text-xs">{selected.customerIdentityId}</dd></div><div><dt className="text-slate-500">Chef identity</dt><dd className="break-all font-mono text-xs">{selected.chefIdentityId ?? "Unassigned"}</dd></div><div><dt className="text-slate-500">Delivery address ID</dt><dd className="break-all font-mono text-xs">{selected.deliveryAddressId}</dd></div></dl>
      <div className="mt-6 grid gap-3 md:grid-cols-[.7fr_1.5fr_auto]"><label className="text-sm font-bold">New status<select value={nextStatus} onChange={event => setNextStatus(event.target.value as AdminSubscriptionStatus)} className="mt-2 min-h-12 w-full rounded-2xl bg-white px-4">{STATUSES.map(status => <option key={status} value={status}>{status}</option>)}</select></label><label className="text-sm font-bold">Required operational reason<input value={reason} maxLength={1000} onChange={event => setReason(event.target.value)} className="mt-2 min-h-12 w-full rounded-2xl bg-white px-4" /></label><button disabled={busy || !reason.trim()} onClick={() => void applyStatus()} className="self-end min-h-12 rounded-2xl bg-[#6930CA] px-5 font-bold text-white disabled:opacity-50">Apply status</button></div>
      <div className="mt-7"><h3 className="text-lg font-bold">Audit history</h3>{history.length === 0 ? <p className="mt-3 text-sm text-slate-600">No status history was returned.</p> : <div className="mt-3 space-y-2">{history.map(entry => <div key={entry.id} className="rounded-2xl bg-white p-4 text-sm"><div className="flex flex-wrap justify-between gap-2"><strong>{entry.oldStatus ?? "CREATED"} → {entry.newStatus}</strong><span className="text-xs text-slate-500">{new Date(entry.createdAt).toLocaleString("en-IN")}</span></div><p className="mt-2 text-slate-600">{entry.reason ?? "No reason recorded"}</p><p className="mt-1 text-xs text-slate-400">Actor: {entry.actorIdentityId ?? "system"}</p></div>)}</div>}</div>
    </section>}
  </div>;
}
