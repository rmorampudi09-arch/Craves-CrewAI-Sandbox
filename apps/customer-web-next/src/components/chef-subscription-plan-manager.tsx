"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import type { ChefMenuItem } from "@/lib/chef-menu-contract";
import type { ChefMealPlan, ChefMealPlanPeriod, ChefMealSchedule } from "@/lib/chef-subscription-plan-contract";

type PlanForm = { name: string; description: string; billingPeriod: ChefMealPlanPeriod; amount: string };
type MealRow = { day: string; mealSlotCode: string; serviceTime: string; menuItemId: string; quantity: string };

const EMPTY_FORM: PlanForm = { name: "", description: "", billingPeriod: "WEEKLY", amount: "" };
const SLOT_DEFAULT_TIME: Record<string, string> = { BREAKFAST: "08:30", LUNCH: "12:30", DINNER: "19:30", SNACK: "16:30" };
const WEEKDAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

function emptyMeal(): MealRow {
  return { day: "1", mealSlotCode: "LUNCH", serviceTime: "12:30", menuItemId: "", quantity: "1" };
}

function statusLabel(status: ChefMealPlan["status"]): string {
  if (status === "PENDING_APPROVAL") return "Waiting for admin approval";
  if (status === "ACTIVE") return "Approved · Live for customers";
  if (status === "REJECTED") return "Changes requested";
  if (status === "INACTIVE") return "Inactive";
  return "Draft";
}

function money(value: number, currency: string): string {
  try { return new Intl.NumberFormat("en-IN", { style: "currency", currency }).format(value); }
  catch { return `${currency} ${value.toFixed(2)}`; }
}

function rowsFromSchedule(schedule: ChefMealSchedule): MealRow[] {
  return schedule.items.map(item => ({
    day: String(schedule.recurrenceType === "WEEKLY" ? item.isoDayOfWeek : item.dayOfMonth),
    mealSlotCode: item.mealSlotCode,
    serviceTime: item.serviceTime.slice(0, 5),
    menuItemId: item.menuItemId,
    quantity: String(item.quantity),
  }));
}

export function ChefSubscriptionPlanManager() {
  const [plans, setPlans] = useState<ChefMealPlan[]>([]);
  const [menu, setMenu] = useState<ChefMenuItem[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [form, setForm] = useState<PlanForm>(EMPTY_FORM);
  const [showNew, setShowNew] = useState(false);
  const [rows, setRows] = useState<MealRow[]>([emptyMeal()]);
  const [timezone] = useState("Asia/Kolkata");
  const [leadHours, setLeadHours] = useState("24");
  const [note, setNote] = useState("");
  const [message, setMessage] = useState("Loading your meal plans…");
  const [busy, setBusy] = useState(false);

  const availableMenu = useMemo(() => menu.filter(item => item.status === "ACTIVE" && item.available), [menu]);
  const selected = useMemo(() => plans.find(plan => plan.id === selectedId) ?? null, [plans, selectedId]);
  const editable = selected?.status === "DRAFT" || selected?.status === "REJECTED";

  const load = useCallback(async () => {
    const [plansResponse, menuResponse] = await Promise.all([
      fetch("/api/chef/subscription-plans", { cache: "no-store" }),
      fetch("/api/chef/menu", { cache: "no-store" }),
    ]);
    if (plansResponse.status === 401 || menuResponse.status === 401) throw new Error("Chef session expired. Sign in again.");
    if (plansResponse.status === 403 || menuResponse.status === 403) throw new Error("Approved CHEF access is required.");
    if (!plansResponse.ok) throw new Error("Your meal plans are temporarily unavailable.");
    if (!menuResponse.ok) throw new Error("Your available menu is temporarily unavailable.");
    const [planBody, menuBody] = await Promise.all([plansResponse.json(), menuResponse.json()]);
    const nextPlans = planBody as ChefMealPlan[];
    setPlans(nextPlans);
    setMenu(menuBody as ChefMenuItem[]);
    setSelectedId(current => current && nextPlans.some(plan => plan.id === current) ? current : nextPlans[0]?.id ?? null);
    setMessage("");
  }, []);

  const loadSchedule = useCallback(async (plan: ChefMealPlan) => {
    const response = await fetch(`/api/chef/subscription-plans/${plan.id}/schedule`, { cache: "no-store" });
    if (response.status === 404) {
      setRows([emptyMeal()]);
      setLeadHours("24");
      return;
    }
    if (!response.ok) throw new Error("Meal schedule could not be loaded.");
    const schedule = await response.json() as ChefMealSchedule;
    setRows(rowsFromSchedule(schedule));
    setLeadHours(String(schedule.generationLeadHours));
  }, []);

  useEffect(() => { void load().catch(error => setMessage(error instanceof Error ? error.message : "Meal plans are unavailable.")); }, [load]);
  useEffect(() => {
    if (!selected) return;
    setForm({ name: selected.name, description: selected.description ?? "", billingPeriod: selected.billingPeriod, amount: String(selected.amount) });
    void loadSchedule(selected).catch(error => setMessage(error instanceof Error ? error.message : "Meal schedule is unavailable."));
  }, [selected, loadSchedule]);

  function setField<K extends keyof PlanForm>(field: K, value: PlanForm[K]) {
    setForm(current => ({ ...current, [field]: value }));
  }

  function updateRow(index: number, field: keyof MealRow, value: string) {
    setRows(current => current.map((row, rowIndex) => {
      if (rowIndex !== index) return row;
      if (field === "mealSlotCode") return { ...row, mealSlotCode: value, serviceTime: SLOT_DEFAULT_TIME[value] ?? row.serviceTime };
      return { ...row, [field]: value };
    }));
  }

  function validatePlanForm(): { name: string; description: string | null; billingPeriod: ChefMealPlanPeriod; amount: number; currency: string } | null {
    const amount = Number(form.amount);
    if (!form.name.trim() || !Number.isFinite(amount) || amount < 0) {
      setMessage("Enter a plan name and a valid non-negative subscription amount.");
      return null;
    }
    return { name: form.name.trim(), description: form.description.trim() || null, billingPeriod: form.billingPeriod, amount, currency: "INR" };
  }

  async function createPlan(event: React.FormEvent) {
    event.preventDefault();
    const payload = validatePlanForm();
    if (!payload) return;
    setBusy(true); setMessage("");
    try {
      const response = await fetch("/api/chef/subscription-plans", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
      const body = await response.json().catch(() => null) as ChefMealPlan | { code?: string } | null;
      if (!response.ok) throw new Error("Meal plan draft could not be created.");
      const created = body as ChefMealPlan;
      setPlans(current => [created, ...current]);
      setSelectedId(created.id);
      setRows([emptyMeal()]);
      setLeadHours("24");
      setShowNew(false);
      setMessage("Draft created. Now choose the meals you can actually prepare.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Meal plan draft could not be created."); }
    finally { setBusy(false); }
  }

  async function saveDetails() {
    if (!selected || !editable) return;
    const payload = validatePlanForm();
    if (!payload) return;
    setBusy(true); setMessage("");
    try {
      const response = await fetch(`/api/chef/subscription-plans/${selected.id}`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
      if (!response.ok) throw new Error("Plan details could not be saved.");
      await load();
      setSelectedId(selected.id);
      setMessage("Plan details saved.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Plan details could not be saved."); }
    finally { setBusy(false); }
  }

  function schedulePayload(plan: ChefMealPlan) {
    const lead = Number(leadHours);
    if (!Number.isInteger(lead) || lead < 1 || lead > 168) { setMessage("Preparation lead time must be between 1 and 168 hours."); return null; }
    if (rows.length < 1) { setMessage("Add at least one meal before submitting."); return null; }
    const items = rows.map((row, index) => {
      const day = Number(row.day), quantity = Number(row.quantity);
      if (!row.menuItemId || !Number.isInteger(day) || !Number.isInteger(quantity) || quantity < 1 || quantity > 100 || !row.serviceTime || !row.mealSlotCode) return null;
      if (plan.billingPeriod === "WEEKLY" && (day < 1 || day > 7)) return null;
      if (plan.billingPeriod === "MONTHLY" && (day < 1 || day > 28)) return null;
      return {
        menuItemId: row.menuItemId,
        quantity,
        isoDayOfWeek: plan.billingPeriod === "WEEKLY" ? day : null,
        dayOfMonth: plan.billingPeriod === "MONTHLY" ? day : null,
        mealSlotCode: row.mealSlotCode,
        serviceTime: row.serviceTime,
        sequenceNumber: index + 1,
      };
    });
    if (items.some(item => item === null)) { setMessage("Complete every meal row before saving."); return null; }
    return { recurrenceType: plan.billingPeriod, timezone, generationLeadHours: lead, items };
  }

  async function saveSchedule(submitAfterSave: boolean) {
    if (!selected || !editable) return;
    const payload = schedulePayload(selected);
    if (!payload) return;
    setBusy(true); setMessage("");
    try {
      const save = await fetch(`/api/chef/subscription-plans/${selected.id}/schedule`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
      if (!save.ok) throw new Error(save.status === 409 ? "One of the selected dishes is no longer active/available or does not belong to your kitchen." : "Meal schedule could not be saved.");
      if (!submitAfterSave) { setMessage("Meal schedule saved as a draft."); return; }
      const submit = await fetch(`/api/chef/subscription-plans/${selected.id}/submit`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ note: note.trim() || null }) });
      const submitBody = await submit.json().catch(() => null) as { details?: { message?: string } } | null;
      if (!submit.ok) throw new Error(submitBody?.details?.message || "Plan could not be submitted for approval.");
      setNote("");
      await load();
      setSelectedId(selected.id);
      setMessage("Submitted for Admin approval. Your meal content is now locked until review.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Meal plan could not be saved."); }
    finally { setBusy(false); }
  }

  function beginNew() {
    setShowNew(true);
    setSelectedId(null);
    setForm(EMPTY_FORM);
    setRows([emptyMeal()]);
    setMessage(availableMenu.length ? "Create the plan first, then add meals from your available menu." : "You need at least one active and available menu item before building a meal plan.");
  }

  return <div className="grid gap-6 xl:grid-cols-[0.78fr_1.22fr]">
    <aside className="space-y-4">
      <button type="button" onClick={beginNew} className="btn-primary w-full justify-center">Create new meal plan</button>
      <section className="rounded-2xl border border-border bg-white p-4 shadow-[var(--shadow-card)]">
        <div className="flex items-center justify-between"><h2 className="font-display text-xl font-bold text-ink">My plans</h2><span className="text-sm text-muted-foreground">{plans.length}</span></div>
        <div className="mt-4 space-y-2">
          {plans.length === 0 && <p className="rounded-xl bg-secondary p-4 text-sm text-muted-foreground">No plans yet. Create your first meal plan.</p>}
          {plans.map(plan => <button type="button" key={plan.id} onClick={() => { setShowNew(false); setSelectedId(plan.id); }} className={`w-full rounded-xl border p-4 text-left transition ${selectedId === plan.id ? "border-primary bg-secondary" : "border-border bg-white hover:border-primary/40"}`}>
            <div className="flex items-start justify-between gap-3"><div className="min-w-0"><p className="truncate font-semibold text-ink">{plan.name}</p><p className="mt-1 text-xs font-semibold text-primary">{statusLabel(plan.status)}</p></div><span className="shrink-0 text-sm font-bold text-ink">{money(plan.amount, plan.currency)}</span></div>
          </button>)}
        </div>
      </section>
      <section className="rounded-2xl border border-border bg-white p-4 text-sm leading-6 text-muted-foreground shadow-[var(--shadow-card)]">
        <strong className="text-ink">How it works</strong>
        <ol className="mt-2 list-decimal space-y-1 pl-5"><li>Create the plan details.</li><li>Pick only dishes you currently offer.</li><li>Set days and meal times.</li><li>Submit once. Admin reviews and approves.</li></ol>
      </section>
    </aside>

    <section className="space-y-5">
      {message && <p role="status" className="rounded-2xl border border-border bg-white p-4 text-sm text-ink shadow-[var(--shadow-card)]">{message}</p>}

      {showNew && <form onSubmit={createPlan} className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
        <p className="craves-overline text-primary">Step 1 of 2</p><h2 className="mt-1 font-display text-2xl font-bold text-ink">Plan details</h2><p className="mt-2 text-sm text-muted-foreground">No plan codes or Chef assignment needed. Craves links the plan to your signed-in Chef account automatically.</p>
        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          <label className="text-sm font-semibold text-ink sm:col-span-2">Plan name<input value={form.name} onChange={event => setField("name", event.target.value)} maxLength={160} placeholder="Weekly home lunch plan" className="mt-2 min-h-12 w-full rounded-xl border border-border bg-white px-4" required /></label>
          <label className="text-sm font-semibold text-ink">Frequency<select value={form.billingPeriod} onChange={event => { const period = event.target.value as ChefMealPlanPeriod; setField("billingPeriod", period); setRows([emptyMeal()]); }} className="mt-2 min-h-12 w-full rounded-xl border border-border bg-white px-4"><option value="WEEKLY">Weekly</option><option value="MONTHLY">Monthly</option></select></label>
          <label className="text-sm font-semibold text-ink">Plan price (₹)<input value={form.amount} onChange={event => setField("amount", event.target.value)} type="number" min="0" step="0.01" className="mt-2 min-h-12 w-full rounded-xl border border-border bg-white px-4" required /></label>
          <label className="text-sm font-semibold text-ink sm:col-span-2">Description<textarea value={form.description} onChange={event => setField("description", event.target.value)} maxLength={2000} placeholder="What customers receive and what makes this plan special" className="mt-2 min-h-28 w-full rounded-xl border border-border bg-white p-4" /></label>
        </div>
        <button disabled={busy} className="btn-primary mt-5 disabled:opacity-50">Continue to meals</button>
      </form>}

      {selected && !showNew && <>
        <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
          <div className="flex flex-wrap items-start justify-between gap-4"><div><p className="craves-overline text-primary">{statusLabel(selected.status)}</p><h2 className="mt-1 font-display text-2xl font-bold text-ink">{selected.name}</h2><p className="mt-2 text-sm text-muted-foreground">{selected.planCode} · {selected.billingPeriod}</p></div><strong className="text-xl text-ink">{money(selected.amount, selected.currency)}</strong></div>
          {selected.reviewReason && <p className="mt-4 rounded-xl border border-primary/20 bg-secondary p-4 text-sm text-ink"><strong>Admin note:</strong> {selected.reviewReason}</p>}
          {editable && <div className="mt-5 grid gap-4 sm:grid-cols-2">
            <label className="text-sm font-semibold text-ink sm:col-span-2">Plan name<input value={form.name} onChange={event => setField("name", event.target.value)} className="mt-2 min-h-11 w-full rounded-xl border border-border px-3" /></label>
            <label className="text-sm font-semibold text-ink">Frequency<select value={form.billingPeriod} onChange={event => setField("billingPeriod", event.target.value as ChefMealPlanPeriod)} className="mt-2 min-h-11 w-full rounded-xl border border-border px-3"><option value="WEEKLY">Weekly</option><option value="MONTHLY">Monthly</option></select></label>
            <label className="text-sm font-semibold text-ink">Price (₹)<input value={form.amount} onChange={event => setField("amount", event.target.value)} type="number" min="0" step="0.01" className="mt-2 min-h-11 w-full rounded-xl border border-border px-3" /></label>
            <label className="text-sm font-semibold text-ink sm:col-span-2">Description<textarea value={form.description} onChange={event => setField("description", event.target.value)} className="mt-2 min-h-20 w-full rounded-xl border border-border p-3" /></label>
            <div className="sm:col-span-2"><button type="button" disabled={busy} onClick={() => void saveDetails()} className="rounded-xl border border-primary px-4 py-2 text-sm font-semibold text-primary disabled:opacity-50">Save details</button></div>
          </div>}
        </section>

        <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
          <p className="craves-overline text-primary">Step 2 of 2</p><h3 className="mt-1 font-display text-2xl font-bold text-ink">Choose meals</h3>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">Only your <strong className="text-ink">active and available</strong> menu dishes can be selected. If a dish is unavailable, update it in Menu first.</p>
          {availableMenu.length === 0 && <p className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">No active, available dishes were found. Add or enable dishes in your Menu before creating a subscription meal schedule.</p>}
          <div className="mt-5 space-y-3">
            {rows.map((row, index) => <div key={index} className="grid gap-3 rounded-2xl bg-secondary p-4 md:grid-cols-[1fr_1fr_1.7fr_.7fr_auto]">
              <label className="text-xs font-bold uppercase tracking-wide text-muted-foreground">{selected.billingPeriod === "WEEKLY" ? "Day" : "Day of month"}{selected.billingPeriod === "WEEKLY" ? <select disabled={!editable} value={row.day} onChange={event => updateRow(index, "day", event.target.value)} className="mt-1 min-h-11 w-full rounded-xl border border-border bg-white px-3 text-sm font-normal normal-case text-ink">{WEEKDAYS.map((day, dayIndex) => <option key={day} value={dayIndex + 1}>{day}</option>)}</select> : <input disabled={!editable} type="number" min="1" max="28" value={row.day} onChange={event => updateRow(index, "day", event.target.value)} className="mt-1 min-h-11 w-full rounded-xl border border-border bg-white px-3 text-sm font-normal text-ink" />}</label>
              <label className="text-xs font-bold uppercase tracking-wide text-muted-foreground">Meal<select disabled={!editable} value={row.mealSlotCode} onChange={event => updateRow(index, "mealSlotCode", event.target.value)} className="mt-1 min-h-11 w-full rounded-xl border border-border bg-white px-3 text-sm font-normal normal-case text-ink"><option value="BREAKFAST">Breakfast</option><option value="LUNCH">Lunch</option><option value="DINNER">Dinner</option><option value="SNACK">Snack</option></select></label>
              <label className="text-xs font-bold uppercase tracking-wide text-muted-foreground">Dish<select disabled={!editable} value={row.menuItemId} onChange={event => updateRow(index, "menuItemId", event.target.value)} className="mt-1 min-h-11 w-full rounded-xl border border-border bg-white px-3 text-sm font-normal normal-case text-ink"><option value="">Choose a dish</option>{availableMenu.map(item => <option key={item.id} value={item.id}>{item.itemName} · {money(item.price, item.currency)}</option>)}</select></label>
              <div className="grid grid-cols-2 gap-2 md:grid-cols-1"><label className="text-xs font-bold uppercase tracking-wide text-muted-foreground">Time<input disabled={!editable} type="time" value={row.serviceTime} onChange={event => updateRow(index, "serviceTime", event.target.value)} className="mt-1 min-h-11 w-full rounded-xl border border-border bg-white px-2 text-sm font-normal text-ink" /></label><label className="text-xs font-bold uppercase tracking-wide text-muted-foreground">Qty<input disabled={!editable} type="number" min="1" max="100" value={row.quantity} onChange={event => updateRow(index, "quantity", event.target.value)} className="mt-1 min-h-11 w-full rounded-xl border border-border bg-white px-2 text-sm font-normal text-ink" /></label></div>
              {editable && <button type="button" aria-label="Remove meal" disabled={rows.length === 1} onClick={() => setRows(current => current.filter((_, rowIndex) => rowIndex !== index))} className="self-end rounded-xl border border-border bg-white px-3 py-2 text-sm font-semibold text-muted-foreground disabled:opacity-40">Remove</button>}
            </div>)}
          </div>
          {editable && <>
            <div className="mt-4 flex flex-wrap gap-3"><button type="button" disabled={busy || availableMenu.length === 0} onClick={() => setRows(current => [...current, emptyMeal()])} className="rounded-xl border border-primary px-4 py-2 text-sm font-semibold text-primary disabled:opacity-50">+ Add meal</button><label className="flex items-center gap-2 text-sm text-muted-foreground">Preparation lead<input type="number" min="1" max="168" value={leadHours} onChange={event => setLeadHours(event.target.value)} className="w-20 rounded-lg border border-border px-2 py-2 text-ink" /> hours</label></div>
            <label className="mt-4 block text-sm font-semibold text-ink">Note for Admin (optional)<textarea maxLength={1000} value={note} onChange={event => setNote(event.target.value)} placeholder="Anything the reviewer should know" className="mt-2 min-h-20 w-full rounded-xl border border-border p-3" /></label>
            <div className="mt-4 flex flex-wrap gap-3"><button type="button" disabled={busy || availableMenu.length === 0} onClick={() => void saveSchedule(false)} className="rounded-xl border border-primary px-5 py-2.5 font-semibold text-primary disabled:opacity-50">Save draft</button><button type="button" disabled={busy || availableMenu.length === 0} onClick={() => void saveSchedule(true)} className="btn-primary disabled:opacity-50">Save & submit for approval</button></div>
          </>}
          {!editable && <p className="mt-4 rounded-xl bg-secondary p-4 text-sm text-muted-foreground">This meal schedule is locked while the plan is {selected.status === "PENDING_APPROVAL" ? "under review" : selected.status === "ACTIVE" ? "live" : "inactive"}.</p>}
        </section>
      </>}

      {!selected && !showNew && <section className="rounded-2xl border border-dashed border-border bg-white p-10 text-center shadow-[var(--shadow-card)]"><h2 className="font-display text-2xl font-bold text-ink">Create your first meal plan</h2><p className="mt-2 text-sm text-muted-foreground">Choose your own dishes, schedule them, and send the plan to Admin for approval.</p><button type="button" onClick={beginNew} className="btn-primary mt-5">Create meal plan</button></section>}
    </section>
  </div>;
}
