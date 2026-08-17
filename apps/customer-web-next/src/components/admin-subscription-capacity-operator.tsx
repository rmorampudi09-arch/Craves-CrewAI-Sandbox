"use client";

import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, Ban, RefreshCw, Search, ShieldCheck, Wrench } from "lucide-react";
import {
  parseCapacityIncidentPage,
  parseChefCapacitySummary,
  type CapacityIncident,
  type ChefCapacitySummary,
} from "@/lib/admin-subscription-capacity-contract";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function errorText(value: unknown, fallback: string): string {
  return value && typeof value === "object" && "message" in value && typeof value.message === "string"
    ? value.message : fallback;
}

export function AdminSubscriptionCapacityOperator() {
  const [chefId, setChefId] = useState("");
  const [summary, setSummary] = useState<ChefCapacitySummary | null>(null);
  const [incidents, setIncidents] = useState<CapacityIncident[]>([]);
  const [freezeReason, setFreezeReason] = useState("");
  const [subscriptionId, setSubscriptionId] = useState("");
  const [reconcileReason, setReconcileReason] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const loadIncidents = useCallback(async (filterChefId?: string) => {
    const query = new URLSearchParams({ status: "OPEN", limit: "100" });
    if (filterChefId) query.set("chefIdentityId", filterChefId);
    const response = await fetch(`/api/admin/subscription-capacity/incidents?${query.toString()}`, { cache: "no-store" });
    const raw = await response.json().catch(() => null);
    if (!response.ok) throw new Error("Capacity incidents are temporarily unavailable.");
    const parsed = parseCapacityIncidentPage(raw);
    if (!parsed) throw new Error("Craves returned an invalid capacity incident response.");
    setIncidents(parsed.items);
  }, []);

  useEffect(() => { void loadIncidents().catch(error => setMessage(error instanceof Error ? error.message : "Capacity incidents are unavailable.")); }, [loadIncidents]);

  async function loadChef() {
    const normalized = chefId.trim();
    if (!UUID.test(normalized)) { setMessage("Enter a valid chef identity UUID."); return; }
    setBusy(true); setMessage("");
    try {
      const response = await fetch(`/api/admin/subscription-capacity/chefs/${normalized}`, { cache: "no-store" });
      const raw = await response.json().catch(() => null);
      if (!response.ok) throw new Error(errorText(raw, "Chef capacity could not be loaded."));
      const parsed = parseChefCapacitySummary(raw);
      if (!parsed) throw new Error("Craves returned an invalid chef capacity response.");
      setSummary(parsed);
      await loadIncidents(normalized);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Chef capacity could not be loaded."); }
    finally { setBusy(false); }
  }

  async function setFrozen(frozen: boolean) {
    if (!summary || !freezeReason.trim()) { setMessage("Enter a reason before freezing or unfreezing subscription sales."); return; }
    setBusy(true); setMessage("");
    try {
      const response = await fetch(`/api/admin/subscription-capacity/chefs/${summary.chefIdentityId}/freeze`, {
        method: "PATCH", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ frozen, reason: freezeReason.trim() }),
      });
      const raw = await response.json().catch(() => null);
      if (!response.ok) throw new Error(errorText(raw, "Capacity sales control could not be changed."));
      const parsed = parseChefCapacitySummary(raw);
      if (!parsed) throw new Error("Craves returned an invalid capacity response.");
      setSummary(parsed); setFreezeReason("");
      setMessage(frozen ? "New subscription sales frozen for this chef. Existing commitments were not cancelled." : "Capacity sales freeze removed.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Capacity sales control failed."); }
    finally { setBusy(false); }
  }

  async function reconcile() {
    if (!UUID.test(subscriptionId.trim()) || !reconcileReason.trim()) { setMessage("Enter a valid subscription UUID and a reconciliation reason."); return; }
    if (!window.confirm("Run audited capacity reconciliation for this subscription? This does not increase the chef's declared capacity.")) return;
    setBusy(true); setMessage("");
    try {
      const response = await fetch(`/api/admin/subscription-capacity/subscriptions/${subscriptionId.trim()}/reconcile`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ reason: reconcileReason.trim() }),
      });
      if (!response.ok) {
        const raw = await response.json().catch(() => null);
        throw new Error(errorText(raw, response.status === 403 ? "Your admin role is read-only for reconciliation." : "Capacity reconciliation failed."));
      }
      setReconcileReason("");
      setMessage("Capacity reconciliation completed and audited.");
      await loadIncidents(summary?.chefIdentityId);
      if (summary) await loadChef();
    } catch (error) { setMessage(error instanceof Error ? error.message : "Capacity reconciliation failed."); }
    finally { setBusy(false); }
  }

  return <div className="space-y-7">
    {message && <p role="status" className="rounded-2xl border border-[#e8e1ee] bg-white p-4 text-sm text-[#554761]">{message}</p>}

    <section className="rounded-[28px] border border-[#e8e1ee] bg-white p-6 shadow-[0_22px_60px_-42px_rgba(56,39,83,0.45)]">
      <div className="flex flex-wrap items-start justify-between gap-4"><div><div className="flex items-center gap-2 text-[#6930ca]"><Search size={19} /><span className="text-xs font-bold uppercase tracking-[0.14em]">Chef capacity lookup</span></div><h2 className="mt-2 text-xl font-bold text-[#251b35]">Inspect the chef-owned capacity contract</h2><p className="mt-1 text-sm text-[#71677d]">Support can inspect and freeze new sales. Capacity values themselves remain chef-controlled.</p></div><button disabled={busy} onClick={() => void loadIncidents(summary?.chefIdentityId)} className="inline-flex items-center gap-2 rounded-xl border border-[#ddd3e5] px-4 py-2 text-sm font-bold"><RefreshCw size={16} />Refresh incidents</button></div>
      <div className="mt-5 flex flex-col gap-3 sm:flex-row"><input value={chefId} onChange={event => setChefId(event.target.value)} placeholder="Chef identity UUID" className="min-h-12 flex-1 rounded-xl border border-[#ddd3e5] px-4" /><button disabled={busy} onClick={() => void loadChef()} className="rounded-xl bg-[#6930ca] px-5 py-3 font-bold text-white disabled:opacity-50">Load chef capacity</button></div>
    </section>

    {summary && <section className="rounded-[28px] border border-[#e8e1ee] bg-white p-6">
      <div className="grid gap-3 md:grid-cols-4"><div className="rounded-2xl bg-[#f7f5fb] p-4"><p className="text-xs font-bold uppercase tracking-wide text-[#8a7a96]">Sales state</p><p className={`mt-2 font-bold ${summary.adminSalesFrozen ? "text-red-700" : "text-emerald-700"}`}>{summary.adminSalesFrozen ? "FROZEN" : "OPEN"}</p></div><div className="rounded-2xl bg-[#f7f5fb] p-4"><p className="text-xs font-bold uppercase tracking-wide text-[#8a7a96]">Recurring rules</p><p className="mt-2 text-2xl font-bold">{summary.slotRules.length}</p></div><div className="rounded-2xl bg-[#f7f5fb] p-4"><p className="text-xs font-bold uppercase tracking-wide text-[#8a7a96]">Date overrides</p><p className="mt-2 text-2xl font-bold">{summary.dateOverrides.length}</p></div><div className="rounded-2xl bg-[#f7f5fb] p-4"><p className="text-xs font-bold uppercase tracking-wide text-[#8a7a96]">Open incidents</p><p className="mt-2 text-2xl font-bold">{summary.openIncidentCount}</p></div></div>
      {summary.freezeReason && <p className="mt-4 rounded-2xl bg-red-50 p-4 text-sm text-red-800"><strong>Freeze reason:</strong> {summary.freezeReason}</p>}
      <div className="mt-5 flex flex-col gap-3 sm:flex-row"><input value={freezeReason} maxLength={1000} onChange={event => setFreezeReason(event.target.value)} placeholder="Required freeze/unfreeze reason" className="min-h-12 flex-1 rounded-xl border border-[#ddd3e5] px-4" />{summary.adminSalesFrozen ? <button disabled={busy} onClick={() => void setFrozen(false)} className="inline-flex items-center justify-center gap-2 rounded-xl border border-[#6930ca] px-5 font-bold text-[#6930ca]"><ShieldCheck size={17} />Unfreeze sales</button> : <button disabled={busy} onClick={() => void setFrozen(true)} className="inline-flex items-center justify-center gap-2 rounded-xl bg-red-700 px-5 font-bold text-white"><Ban size={17} />Freeze new sales</button>}</div>
      <div className="mt-6 overflow-x-auto"><table className="min-w-[800px] w-full text-left text-sm"><thead><tr className="border-b text-[#71677d]"><th className="p-3">Day</th><th className="p-3">Slot</th><th className="p-3">Total</th><th className="p-3">Subscription</th><th className="p-3">Reserved</th><th className="p-3">Available</th><th className="p-3">Deficit</th></tr></thead><tbody>{summary.slotRules.map(rule => <tr key={rule.id} className="border-b border-[#eee8f2]"><td className="p-3">{rule.isoDayOfWeek}</td><td className="p-3 font-bold">{rule.mealSlotCode}</td><td className="p-3">{rule.totalCapacityUnits}</td><td className="p-3">{rule.subscriptionCapacityUnits}</td><td className="p-3">{rule.recurringReservedUnits}</td><td className="p-3 text-emerald-700">{rule.recurringAvailableUnits}</td><td className={`p-3 font-bold ${rule.recurringDeficitUnits ? "text-red-700" : "text-[#8a7a96]"}`}>{rule.recurringDeficitUnits}</td></tr>)}</tbody></table></div>
    </section>}

    <section className="rounded-[28px] border border-[#e8e1ee] bg-white p-6">
      <div className="flex items-center gap-2"><AlertTriangle className="text-amber-600" size={19} /><h2 className="text-xl font-bold">Open capacity incidents</h2></div>
      <p className="mt-1 text-sm text-[#71677d]">A P2 deficit means existing commitments exceed a newer chef limit/closure. Existing subscribers remain protected while new sales are blocked.</p>
      <div className="mt-5 space-y-3">{incidents.length === 0 ? <p className="rounded-2xl bg-[#f7f5fb] p-4 text-sm text-[#71677d]">No open capacity incidents match the current filter.</p> : incidents.map(item => <article key={item.id} className="rounded-2xl border border-[#e8e1ee] p-4"><div className="flex flex-wrap items-start justify-between gap-2"><div><strong>{item.severity} · {item.incidentType.replaceAll("_", " ")}</strong><p className="mt-1 text-xs text-[#8a7a96]">Chef {item.chefIdentityId} · {item.serviceDate ?? `weekday ${item.isoDayOfWeek ?? "—"}`} · {item.mealSlotCode}</p></div><span className="rounded-full bg-red-50 px-3 py-1 text-xs font-bold text-red-700">{item.status}</span></div><p className="mt-3 text-sm text-[#554761]">{item.reason}</p><p className="mt-2 text-sm"><strong>Reserved:</strong> {item.reservedUnits} &nbsp; <strong>Configured capacity:</strong> {item.capacityUnits}</p></article>)}</div>
    </section>

    <section className="rounded-[28px] border border-[#e8e1ee] bg-white p-6">
      <div className="flex items-center gap-2"><Wrench className="text-[#6930ca]" size={19} /><h2 className="text-xl font-bold">Audited subscription reconciliation</h2></div><p className="mt-1 text-sm text-[#71677d]">For Operations/Subscription Admin use after an incident or data repair. Reconciliation restores/releases reservation records to match subscription state; it never raises the chef&apos;s configured capacity.</p>
      <div className="mt-5 grid gap-3 lg:grid-cols-[1fr_1.5fr_auto]"><input value={subscriptionId} onChange={event => setSubscriptionId(event.target.value)} placeholder="Subscription UUID" className="min-h-12 rounded-xl border border-[#ddd3e5] px-4" /><input value={reconcileReason} maxLength={1000} onChange={event => setReconcileReason(event.target.value)} placeholder="Required reconciliation reason / incident reference" className="min-h-12 rounded-xl border border-[#ddd3e5] px-4" /><button disabled={busy} onClick={() => void reconcile()} className="rounded-xl bg-[#6930ca] px-5 font-bold text-white disabled:opacity-50">Reconcile</button></div>
    </section>
  </div>;
}
