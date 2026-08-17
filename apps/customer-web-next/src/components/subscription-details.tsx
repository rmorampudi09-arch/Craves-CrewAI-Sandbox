"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { CustomerSubscription } from "@/lib/subscription-contract";
import type { SubscriptionOccurrence, SubscriptionPlanPolicy } from "@/lib/subscription-lifecycle-contract";
import type { PublicSubscriptionSchedule } from "@/lib/subscription-schedule-contract";

function formatDate(value: string | null): string {
  if (!value) return "Not scheduled";
  return new Date(`${value}T00:00:00Z`).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });
}

function formatSlot(value: string): string {
  return value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, letter => letter.toUpperCase());
}

function policyHint(enabled: boolean, minutes: number | null, label: string): string {
  if (!enabled) return `${label} is disabled for this plan.`;
  return minutes == null ? `${label} is enabled.` : `${label} is allowed until ${minutes} minutes before the applicable meal.`;
}

export function SubscriptionDetails({ subscriptionId }: { subscriptionId: string }) {
  const [item, setItem] = useState<CustomerSubscription | null>(null);
  const [policy, setPolicy] = useState<SubscriptionPlanPolicy | null>(null);
  const [schedule, setSchedule] = useState<PublicSubscriptionSchedule | null>(null);
  const [occurrences, setOccurrences] = useState<SubscriptionOccurrence[]>([]);
  const [message, setMessage] = useState("Loading subscription…");
  const [busy, setBusy] = useState(false);
  const [resumeDate, setResumeDate] = useState("");
  const [skipDate, setSkipDate] = useState("");

  const load = useCallback(async () => {
    const subscriptionResponse = await fetch(`/api/subscriptions/${subscriptionId}`, { cache: "no-store" });
    const subscriptionBody = await subscriptionResponse.json().catch(() => null);
    if (subscriptionResponse.status === 401) throw new Error("Your session expired. Sign in again.");
    if (!subscriptionResponse.ok) throw new Error(subscriptionResponse.status === 404 ? "Subscription was not found." : "Subscription is temporarily unavailable.");
    const current = subscriptionBody as CustomerSubscription;
    setItem(current);
    setResumeDate(value => value || current.nextServiceDate || current.startDate);
    setSkipDate(value => value || current.nextServiceDate || "");

    const [policyResponse, scheduleResponse, occurrenceResponse] = await Promise.all([
      fetch(`/api/subscriptions/plans/${current.planId}/policy`, { cache: "no-store" }),
      fetch(`/api/subscriptions/plans/${current.planId}/schedule`, { cache: "no-store" }),
      fetch(`/api/subscriptions/${subscriptionId}/occurrences?limit=100`, { cache: "no-store" }),
    ]);

    if (policyResponse.ok) setPolicy(await policyResponse.json() as SubscriptionPlanPolicy); else setPolicy(null);
    if (scheduleResponse.ok) setSchedule(await scheduleResponse.json() as PublicSubscriptionSchedule); else setSchedule(null);
    if (occurrenceResponse.ok) setOccurrences(await occurrenceResponse.json() as SubscriptionOccurrence[]); else setOccurrences([]);
    setMessage("");
  }, [subscriptionId]);

  useEffect(() => { void load().catch(error => setMessage(error instanceof Error ? error.message : "Subscription is unavailable.")); }, [load]);

  async function stateAction(action: "pause" | "cancel") {
    const promptLabel = action === "pause" ? "Optional pause reason" : "Optional cancellation reason";
    const reason = window.prompt(promptLabel) ?? "";
    if (action === "cancel" && !window.confirm("Cancel this meal subscription? Future undispatched meals will be stopped. Any refund or unused-meal treatment follows the administrator-approved policy, not this screen.")) return;
    setBusy(true); setMessage("");
    try {
      const response = await fetch(`/api/subscriptions/${subscriptionId}/${action}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ reason: reason.trim() || null }) });
      if (!response.ok) throw new Error(response.status === 409 ? `The administrator ${action} cutoff has passed or the subscription state changed.` : `${action === "pause" ? "Pause" : "Cancellation"} could not be completed.`);
      await load();
      setMessage(action === "pause" ? "Subscription paused." : "Subscription cancelled.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Subscription update failed."); }
    finally { setBusy(false); }
  }

  async function resume() {
    if (!resumeDate) { setMessage("Choose a resume date."); return; }
    const reason = window.prompt("Optional resume reason") ?? "";
    setBusy(true); setMessage("");
    try {
      const response = await fetch(`/api/subscriptions/${subscriptionId}/resume`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ resumeDate, reason: reason.trim() || null }) });
      if (!response.ok) throw new Error(response.status === 409 ? "The selected date is outside the administrator-approved resume window or is no longer valid." : "Subscription could not be resumed.");
      await load(); setMessage("Subscription resumed.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Subscription resume failed."); }
    finally { setBusy(false); }
  }

  async function skip() {
    if (!skipDate) { setMessage("Choose a scheduled meal date to skip."); return; }
    const reason = window.prompt("Optional skip reason") ?? "";
    setBusy(true); setMessage("");
    try {
      const response = await fetch(`/api/subscriptions/${subscriptionId}/skips`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ serviceDate: skipDate, reason: reason.trim() || null }) });
      if (!response.ok) throw new Error(response.status === 409 ? "That meal is outside the administrator-approved skip window or has progressed too far." : "Meal could not be skipped.");
      await load(); setMessage(`Meal date ${skipDate} marked to skip.`);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Meal skip failed."); }
    finally { setBusy(false); }
  }

  const scheduleGroups = useMemo(() => {
    if (!schedule) return [];
    const groups = new Map<string, typeof schedule.items>();
    for (const entry of schedule.items) {
      const day = schedule.recurrenceType === "WEEKLY" ? `weekday-${entry.isoDayOfWeek}` : `monthday-${entry.dayOfMonth}`;
      const key = `${day}:${entry.mealSlotCode}:${entry.serviceTime}`;
      groups.set(key, [...(groups.get(key) ?? []), entry]);
    }
    return [...groups.entries()];
  }, [schedule]);

  if (!item) return <section className="rounded-[28px] bg-[#FFF8EC] p-6 text-slate-950"><p role="status">{message}</p></section>;
  const today = new Date().toISOString().slice(0, 10);

  return <div className="space-y-6">
    <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950 sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">{item.status.replaceAll("_", " ")}</p><h2 className="mt-3 text-3xl font-bold">Meal subscription</h2></div><button type="button" onClick={() => void load()} className="rounded-2xl border border-[#6930CA] px-4 py-2 text-sm font-bold text-[#6930CA]">Refresh</button></div>
      <dl className="mt-6 grid gap-4 sm:grid-cols-2"><div><dt className="text-sm text-slate-500">Start date</dt><dd className="font-semibold">{formatDate(item.startDate)}</dd></div><div><dt className="text-sm text-slate-500">Next service date</dt><dd className="font-semibold">{formatDate(item.nextServiceDate)}</dd></div><div><dt className="text-sm text-slate-500">End date</dt><dd className="font-semibold">{item.endDate ? formatDate(item.endDate) : "Not ended"}</dd></div><div><dt className="text-sm text-slate-500">Delivery address</dt><dd className="font-semibold">{item.deliveryAddressId ? "Saved address selected" : "Not selected"}</dd></div></dl>
      {item.notes && <div className="mt-6 rounded-2xl bg-white p-4 text-sm leading-6"><strong>Notes</strong><p className="mt-2">{item.notes}</p></div>}
      {message && <p className="mt-5 rounded-2xl bg-white p-4 text-sm text-slate-700" role="status">{message}</p>}
    </section>

    <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950 sm:p-8">
      <h3 className="text-2xl font-bold">Manage subscription</h3>
      {!policy && <p className="mt-3 text-sm text-amber-800">The active lifecycle policy is unavailable, so customer self-service actions are hidden rather than guessed.</p>}
      {policy && <div className="mt-4 grid gap-3 text-sm sm:grid-cols-2"><p className="rounded-2xl bg-white p-4">{policyHint(policy.customerPauseEnabled, policy.pauseCutoffMinutes, "Pause")}</p><p className="rounded-2xl bg-white p-4">{policyHint(policy.customerResumeEnabled, policy.resumeLeadMinutes, "Resume")}</p><p className="rounded-2xl bg-white p-4">{policyHint(policy.customerCancelEnabled, policy.cancelCutoffMinutes, "Cancellation")}</p><p className="rounded-2xl bg-white p-4">{policyHint(policy.customerSkipEnabled, policy.skipCutoffMinutes, "Skip")}</p></div>}
      {policy && item.status === "ACTIVE" && <div className="mt-5 flex flex-wrap gap-3">{policy.customerPauseEnabled && <button disabled={busy} onClick={() => void stateAction("pause")} className="rounded-2xl border border-[#6930CA] px-4 py-2 font-bold text-[#6930CA] disabled:opacity-50">Pause subscription</button>}{policy.customerCancelEnabled && <button disabled={busy} onClick={() => void stateAction("cancel")} className="rounded-2xl border border-red-600 px-4 py-2 font-bold text-red-700 disabled:opacity-50">Cancel subscription</button>}</div>}
      {policy && item.status === "PAUSED" && <div className="mt-5 grid gap-3 sm:grid-cols-[1fr_auto_auto]"><input type="date" min={today} value={resumeDate} onChange={event => setResumeDate(event.target.value)} className="min-h-12 rounded-2xl bg-white px-4" />{policy.customerResumeEnabled && <button disabled={busy} onClick={() => void resume()} className="rounded-2xl bg-[#6930CA] px-5 py-3 font-bold text-white disabled:opacity-50">Resume</button>}{policy.customerCancelEnabled && <button disabled={busy} onClick={() => void stateAction("cancel")} className="rounded-2xl border border-red-600 px-5 py-3 font-bold text-red-700 disabled:opacity-50">Cancel</button>}</div>}
      {policy?.customerSkipEnabled && item.status === "ACTIVE" && <div className="mt-5 rounded-2xl bg-white p-4"><label className="text-sm font-bold">Skip a scheduled service date<div className="mt-2 flex flex-wrap gap-2"><input type="date" min={today} value={skipDate} onChange={event => setSkipDate(event.target.value)} className="min-h-11 flex-1 rounded-xl border px-3" /><button disabled={busy || !skipDate} onClick={() => void skip()} type="button" className="rounded-xl border border-[#6930CA] px-4 py-2 font-bold text-[#6930CA] disabled:opacity-50">Skip date</button></div></label><p className="mt-2 text-xs text-slate-500">The backend validates that the date belongs to the active schedule and applies the skip atomically to all meal slots on that date before order dispatch.</p></div>}
      {policy && <div className="mt-5 grid gap-2 text-xs text-slate-500 sm:grid-cols-3"><p>Holiday policy: {policy.holidayPolicyReference ?? "No customer-facing reference configured"}</p><p>Unused-meal policy: {policy.unusedMealPolicyReference ?? "No customer-facing reference configured"}</p><p>Refund policy: {policy.refundPolicyReference ?? "No customer-facing reference configured"}</p></div>}
    </section>

    <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950 sm:p-8">
      <h3 className="text-2xl font-bold">Plan meal schedule</h3>
      {!schedule && <p className="mt-3 text-sm text-slate-600">Active meal schedule is temporarily unavailable.</p>}
      {schedule && <><p className="mt-2 text-sm text-slate-600">{schedule.recurrenceType} · {schedule.timezone}. Meal slots and contents are administrator-managed and validated against the assigned chef.</p><div className="mt-4 grid gap-3 lg:grid-cols-2">{scheduleGroups.map(([key, entries]) => { const first = entries[0]; const day = schedule.recurrenceType === "WEEKLY" ? `Weekday ${first.isoDayOfWeek}` : `Day ${first.dayOfMonth}`; return <article key={key} className="rounded-2xl bg-white p-4"><p className="text-xs font-bold uppercase tracking-wide text-[#6930CA]">{day} · {formatSlot(first.mealSlotCode)} · {first.serviceTime.slice(0, 5)}</p><ul className="mt-3 space-y-2">{entries.sort((a, b) => a.sequenceNumber - b.sequenceNumber).map(entry => <li key={`${entry.menuItemId}:${entry.sequenceNumber}`} className="text-sm"><strong>{entry.itemName ?? "Meal item"}</strong> × {entry.quantity}{!entry.itemName && <span className="ml-2 text-xs text-slate-400">{entry.menuItemId}</span>}</li>)}</ul></article>; })}</div></>}
    </section>

    <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950 sm:p-8">
      <h3 className="text-2xl font-bold">Scheduled meals</h3>
      <p className="mt-2 text-sm text-slate-600">Generated occurrences are duplicate-safe per subscription, service date and meal slot.</p>
      {occurrences.length === 0 ? <p className="mt-4 rounded-2xl bg-white p-4 text-sm text-slate-600">No generated meal occurrences yet.</p> : <div className="mt-4 space-y-3">{occurrences.map(occurrence => <article key={occurrence.id} className="rounded-2xl bg-white p-4"><div className="flex flex-wrap items-center justify-between gap-2"><div><strong>{formatDate(occurrence.serviceDate)} · {formatSlot(occurrence.mealSlotCode)}</strong><p className="mt-1 text-xs text-slate-500">{new Date(occurrence.serviceAt).toLocaleString("en-IN")}</p></div><span className="rounded-full bg-[#FFF8EC] px-3 py-1 text-xs font-bold">{occurrence.status.replaceAll("_", " ")}</span></div><p className="mt-3 text-xs text-slate-500">{occurrence.items.length} scheduled item{occurrence.items.length === 1 ? "" : "s"}</p></article>)}</div>}
      <p className="mt-5 text-xs text-slate-500">Created {new Date(item.createdAt).toLocaleString("en-IN")}</p>
    </section>
  </div>;
}
