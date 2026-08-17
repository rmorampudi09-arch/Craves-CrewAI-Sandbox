"use client";

import { useCallback, useEffect, useState } from "react";
import type { AdminSubscriptionPlan } from "@/lib/admin-subscription-plan-contract";
import type { AdminSubscriptionPolicy } from "@/lib/admin-subscription-runtime-contract";

type Form = {
  customerPauseEnabled: boolean; customerResumeEnabled: boolean; customerCancelEnabled: boolean; customerSkipEnabled: boolean;
  pauseCutoffMinutes: string; resumeLeadMinutes: string; cancelCutoffMinutes: string; skipCutoffMinutes: string;
  holidayPolicyReference: string; unusedMealPolicyReference: string; refundPolicyReference: string; notes: string;
};
const EMPTY: Form = { customerPauseEnabled: false, customerResumeEnabled: false, customerCancelEnabled: false, customerSkipEnabled: false, pauseCutoffMinutes: "", resumeLeadMinutes: "", cancelCutoffMinutes: "", skipCutoffMinutes: "", holidayPolicyReference: "", unusedMealPolicyReference: "", refundPolicyReference: "", notes: "" };
function minutes(value: string): number | null { if (!value.trim()) return null; const parsed = Number(value); return Number.isInteger(parsed) && parsed >= 0 ? parsed : Number.NaN; }
function fromPolicy(value: AdminSubscriptionPolicy): Form { return { customerPauseEnabled: value.customerPauseEnabled, customerResumeEnabled: value.customerResumeEnabled, customerCancelEnabled: value.customerCancelEnabled, customerSkipEnabled: value.customerSkipEnabled, pauseCutoffMinutes: value.pauseCutoffMinutes?.toString() ?? "", resumeLeadMinutes: value.resumeLeadMinutes?.toString() ?? "", cancelCutoffMinutes: value.cancelCutoffMinutes?.toString() ?? "", skipCutoffMinutes: value.skipCutoffMinutes?.toString() ?? "", holidayPolicyReference: value.holidayPolicyReference ?? "", unusedMealPolicyReference: value.unusedMealPolicyReference ?? "", refundPolicyReference: value.refundPolicyReference ?? "", notes: value.notes ?? "" }; }

export function AdminSubscriptionPolicyManager({ plan, onChanged }: { plan: AdminSubscriptionPlan; onChanged: () => Promise<void> }) {
  const [policy, setPolicy] = useState<AdminSubscriptionPolicy | null>(null);
  const [form, setForm] = useState<Form>(EMPTY);
  const [reason, setReason] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const load = useCallback(async () => {
    const response = await fetch(`/api/admin/subscription-plans/${plan.id}/policy`, { cache: "no-store" });
    if (response.status === 404) { setPolicy(null); setForm(EMPTY); return; }
    if (response.status === 401) throw new Error("Administrator session expired.");
    if (response.status === 403) throw new Error("Subscription administrator access is required.");
    if (!response.ok) throw new Error("Lifecycle policy is unavailable.");
    const value = await response.json() as AdminSubscriptionPolicy; setPolicy(value); setForm(fromPolicy(value));
  }, [plan.id]);
  useEffect(() => { void load().catch(error => setMessage(error instanceof Error ? error.message : "Lifecycle policy is unavailable.")); }, [load]);
  function field<K extends keyof Form>(name: K, value: Form[K]) { setForm(current => ({ ...current, [name]: value })); }

  async function save() {
    const pauseCutoffMinutes = minutes(form.pauseCutoffMinutes), resumeLeadMinutes = minutes(form.resumeLeadMinutes), cancelCutoffMinutes = minutes(form.cancelCutoffMinutes), skipCutoffMinutes = minutes(form.skipCutoffMinutes);
    if ([pauseCutoffMinutes, resumeLeadMinutes, cancelCutoffMinutes, skipCutoffMinutes].some(value => Number.isNaN(value))) { setMessage("Cutoffs must be non-negative whole minutes."); return; }
    if ((form.customerPauseEnabled && pauseCutoffMinutes == null) || (form.customerResumeEnabled && resumeLeadMinutes == null) || (form.customerCancelEnabled && cancelCutoffMinutes == null) || (form.customerSkipEnabled && skipCutoffMinutes == null)) { setMessage("Every enabled customer action requires an explicit admin cutoff/lead time."); return; }
    setBusy(true); setMessage("");
    try {
      const response = await fetch(`/api/admin/subscription-plans/${plan.id}/policy`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ customerPauseEnabled: form.customerPauseEnabled, customerResumeEnabled: form.customerResumeEnabled, customerCancelEnabled: form.customerCancelEnabled, customerSkipEnabled: form.customerSkipEnabled, pauseCutoffMinutes, resumeLeadMinutes, cancelCutoffMinutes, skipCutoffMinutes, holidayPolicyReference: form.holidayPolicyReference.trim() || null, unusedMealPolicyReference: form.unusedMealPolicyReference.trim() || null, refundPolicyReference: form.refundPolicyReference.trim() || null, notes: form.notes.trim() || null }) });
      if (!response.ok) throw new Error("Lifecycle policy draft could not be saved.");
      await load(); await onChanged(); setMessage("Policy draft saved. No cancellation/refund/unused-meal rule was inferred.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Policy draft could not be saved."); } finally { setBusy(false); }
  }

  async function activate() {
    if (!reason.trim()) { setMessage("Enter an activation reason."); return; }
    setBusy(true); setMessage("");
    try {
      const response = await fetch(`/api/admin/subscription-plans/${plan.id}/policy/activate`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ reason: reason.trim() }) });
      if (!response.ok) throw new Error("Lifecycle policy could not be activated.");
      setReason(""); await load(); await onChanged(); setMessage("Lifecycle policy activated.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Policy activation failed."); } finally { setBusy(false); }
  }

  const controls = [
    ["Pause", "customerPauseEnabled", "pauseCutoffMinutes", "Cutoff before next meal (minutes)"],
    ["Resume", "customerResumeEnabled", "resumeLeadMinutes", "Lead before resumed meal (minutes)"],
    ["Cancel", "customerCancelEnabled", "cancelCutoffMinutes", "Cutoff before next meal (minutes)"],
    ["Skip", "customerSkipEnabled", "skipCutoffMinutes", "Cutoff before meal (minutes)"],
  ] as const;

  return <section className="rounded-[24px] bg-white p-5 text-slate-950">
    <h4 className="text-lg font-bold">Customer lifecycle policy</h4><p className="mt-1 text-xs text-slate-500">{policy ? `${policy.status} · version ${policy.version}` : "No policy configured"}. Admin controls customer actions and the exact cutoff values; references preserve externally approved holiday, unused-meal and refund policies without hardcoding them.</p>
    <div className="mt-4 grid gap-3 md:grid-cols-2">{controls.map(([label, flag, cutoff, placeholder]) => <div key={label} className="rounded-2xl bg-[#FFF8EC] p-4"><label className="flex items-center gap-2 font-bold"><input type="checkbox" checked={form[flag]} onChange={event => field(flag, event.target.checked)} /> Allow {label.toLowerCase()}</label><input type="number" min="0" disabled={!form[flag]} value={form[cutoff]} onChange={event => field(cutoff, event.target.value)} placeholder={placeholder} className="mt-3 min-h-10 w-full rounded-xl bg-white px-3 text-sm disabled:opacity-50" /></div>)}</div>
    <div className="mt-4 grid gap-3 md:grid-cols-3"><input maxLength={200} value={form.holidayPolicyReference} onChange={event => field("holidayPolicyReference", event.target.value)} placeholder="Holiday policy reference" className="min-h-10 rounded-xl border px-3 text-sm" /><input maxLength={200} value={form.unusedMealPolicyReference} onChange={event => field("unusedMealPolicyReference", event.target.value)} placeholder="Unused-meal policy reference" className="min-h-10 rounded-xl border px-3 text-sm" /><input maxLength={200} value={form.refundPolicyReference} onChange={event => field("refundPolicyReference", event.target.value)} placeholder="Refund policy reference" className="min-h-10 rounded-xl border px-3 text-sm" /></div>
    <textarea maxLength={4000} value={form.notes} onChange={event => field("notes", event.target.value)} placeholder="Administrator notes" className="mt-3 min-h-20 w-full rounded-xl border p-3 text-sm" />
    <div className="mt-4 flex flex-wrap gap-2"><button type="button" disabled={busy} onClick={() => void save()} className="rounded-xl bg-[#6930CA] px-4 py-2 font-bold text-white">Save policy draft</button><input maxLength={1000} value={reason} onChange={event => setReason(event.target.value)} placeholder="Activation reason" className="min-h-10 flex-1 rounded-xl border px-3 text-sm" /><button type="button" disabled={busy || policy?.status !== "DRAFT"} onClick={() => void activate()} className="rounded-xl border border-[#6930CA] px-4 py-2 font-bold text-[#6930CA] disabled:opacity-40">Activate policy</button></div>
    {message && <p className="mt-3 text-sm text-slate-600" role="status">{message}</p>}
  </section>;
}
