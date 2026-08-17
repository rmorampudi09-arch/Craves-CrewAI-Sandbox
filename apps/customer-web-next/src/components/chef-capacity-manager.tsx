"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AlertTriangle, CalendarDays, Gauge, RefreshCw, Utensils } from "lucide-react";
import {
  parseChefCapacitySummary,
  type ChefCapacitySummary,
} from "@/lib/chef-subscription-capacity-contract";
import { parseChefMenuItems, type ChefMenuItem } from "@/lib/chef-menu-contract";

const DAYS = [
  [1, "Monday"], [2, "Tuesday"], [3, "Wednesday"], [4, "Thursday"],
  [5, "Friday"], [6, "Saturday"], [7, "Sunday"],
] as const;

function number(value: string): number | null {
  if (!value.trim()) return null;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 && parsed <= 100000 ? parsed : null;
}

function message(value: unknown, fallback: string): string {
  return value && typeof value === "object" && "message" in value && typeof value.message === "string"
    ? value.message : fallback;
}

export function ChefCapacityManager() {
  const [summary, setSummary] = useState<ChefCapacitySummary | null>(null);
  const [menu, setMenu] = useState<ChefMenuItem[]>([]);
  const [status, setStatus] = useState("Loading capacity…");
  const [busy, setBusy] = useState(false);

  const [day, setDay] = useState("1");
  const [slot, setSlot] = useState("");
  const [total, setTotal] = useState("");
  const [subscription, setSubscription] = useState("");
  const [salesEnabled, setSalesEnabled] = useState(true);
  const [ruleReason, setRuleReason] = useState("");

  const [date, setDate] = useState("");
  const [dateSlot, setDateSlot] = useState("");
  const [dateTotal, setDateTotal] = useState("");
  const [dateSubscription, setDateSubscription] = useState("");
  const [dateClosed, setDateClosed] = useState(false);
  const [dateReason, setDateReason] = useState("");

  const [menuItemId, setMenuItemId] = useState("");
  const [menuDay, setMenuDay] = useState("1");
  const [menuSlot, setMenuSlot] = useState("");
  const [menuLimit, setMenuLimit] = useState("");
  const [menuSalesEnabled, setMenuSalesEnabled] = useState(true);
  const [menuReason, setMenuReason] = useState("");

  const [menuDateItemId, setMenuDateItemId] = useState("");
  const [menuDate, setMenuDate] = useState("");
  const [menuDateSlot, setMenuDateSlot] = useState("");
  const [menuDateLimit, setMenuDateLimit] = useState("");
  const [menuDateClosed, setMenuDateClosed] = useState(false);
  const [menuDateReason, setMenuDateReason] = useState("");

  const load = useCallback(async () => {
    const [capacityResponse, menuResponse] = await Promise.all([
      fetch("/api/chef/subscription-capacity", { cache: "no-store", credentials: "same-origin" }),
      fetch("/api/chef/menu", { cache: "no-store", credentials: "same-origin" }),
    ]);
    const [capacityRaw, menuRaw] = await Promise.all([
      capacityResponse.json().catch(() => null), menuResponse.json().catch(() => null),
    ]);
    if (!capacityResponse.ok) throw new Error(message(capacityRaw, "Subscription capacity could not be loaded."));
    if (!menuResponse.ok) throw new Error(message(menuRaw, "Chef menu could not be loaded."));
    const parsedCapacity = parseChefCapacitySummary(capacityRaw);
    const parsedMenu = parseChefMenuItems(menuRaw);
    if (!parsedCapacity) throw new Error("Craves returned an invalid capacity response.");
    if (!parsedMenu) throw new Error("Craves returned an invalid chef menu response.");
    setSummary(parsedCapacity);
    setMenu(parsedMenu.filter(item => item.status === "ACTIVE"));
    setStatus("");
  }, []);

  useEffect(() => { void load().catch(error => setStatus(error instanceof Error ? error.message : "Capacity is unavailable.")); }, [load]);

  async function save(path: string, payload: unknown, success: string) {
    setBusy(true); setStatus("");
    try {
      const response = await fetch(path, {
        method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload), credentials: "same-origin",
      });
      const body = await response.json().catch(() => null);
      if (!response.ok) throw new Error(message(body, "Capacity configuration could not be saved."));
      await load();
      setStatus(success);
    } catch (error) {
      setStatus(error instanceof Error ? error.message : "Capacity configuration could not be saved.");
    } finally {
      setBusy(false);
    }
  }

  async function saveSlotRule() {
    const totalUnits = number(total), subscriptionUnits = number(subscription);
    if (!slot.trim() || totalUnits === null || subscriptionUnits === null || subscriptionUnits > totalUnits || !ruleReason.trim()) {
      setStatus("Enter a slot code, valid total/subscription limits and a change reason. Subscription allocation cannot exceed total kitchen capacity."); return;
    }
    await save("/api/chef/subscription-capacity/rules/slots", {
      isoDayOfWeek: Number(day), mealSlotCode: slot.trim().toUpperCase(), totalCapacityUnits: totalUnits,
      subscriptionCapacityUnits: subscriptionUnits, salesEnabled, reason: ruleReason.trim(),
    }, "Recurring meal-slot capacity saved.");
  }

  async function saveDateOverride() {
    const totalUnits = number(dateTotal), subscriptionUnits = number(dateSubscription);
    if (!date || !dateSlot.trim() || totalUnits === null || subscriptionUnits === null || subscriptionUnits > totalUnits || !dateReason.trim()) {
      setStatus("Enter a date, slot, valid limits and a reason for the override."); return;
    }
    await save("/api/chef/subscription-capacity/overrides/slots", {
      serviceDate: date, mealSlotCode: dateSlot.trim().toUpperCase(), totalCapacityUnits: totalUnits,
      subscriptionCapacityUnits: subscriptionUnits, closed: dateClosed, reason: dateReason.trim(),
    }, "Date capacity override saved.");
  }

  async function saveMenuRule() {
    const limit = number(menuLimit);
    if (!menuItemId || !menuSlot.trim() || limit === null || !menuReason.trim()) {
      setStatus("Choose a menu item and enter its recurring limit, slot and reason."); return;
    }
    await save("/api/chef/subscription-capacity/rules/menu-items", {
      menuItemId, isoDayOfWeek: Number(menuDay), mealSlotCode: menuSlot.trim().toUpperCase(),
      maxSubscriptionUnits: limit, salesEnabled: menuSalesEnabled, reason: menuReason.trim(),
    }, "Menu-item capacity rule saved.");
  }

  async function saveMenuDateOverride() {
    const limit = number(menuDateLimit);
    if (!menuDateItemId || !menuDate || !menuDateSlot.trim() || limit === null || !menuDateReason.trim()) {
      setStatus("Choose a menu item/date and enter its limit, slot and reason."); return;
    }
    await save("/api/chef/subscription-capacity/overrides/menu-items", {
      menuItemId: menuDateItemId, serviceDate: menuDate, mealSlotCode: menuDateSlot.trim().toUpperCase(),
      maxSubscriptionUnits: limit, closed: menuDateClosed, reason: menuDateReason.trim(),
    }, "Menu-item date override saved.");
  }

  const menuNames = useMemo(() => new Map(menu.map(item => [item.id, item.itemName])), [menu]);
  const today = new Date().toISOString().slice(0, 10);

  return <div className="space-y-6">
    {summary?.adminSalesFrozen && <section className="rounded-2xl border border-error/25 bg-error/5 p-5 text-error"><div className="flex gap-3"><AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" /><div><h2 className="font-bold">New subscription sales are frozen by Craves operations</h2><p className="mt-1 text-sm">{summary.freezeReason ?? "Contact support before changing subscription availability."}</p></div></div></section>}

    <section className="grid gap-4 sm:grid-cols-3">
      <div className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]"><Gauge className="h-5 w-5 text-primary" /><p className="mt-3 text-xs font-bold uppercase tracking-wide text-muted-foreground">Recurring slot rules</p><p className="mt-1 font-display text-3xl font-bold text-ink">{summary?.slotRules.length ?? 0}</p></div>
      <div className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]"><CalendarDays className="h-5 w-5 text-primary" /><p className="mt-3 text-xs font-bold uppercase tracking-wide text-muted-foreground">Date overrides</p><p className="mt-1 font-display text-3xl font-bold text-ink">{summary?.dateOverrides.length ?? 0}</p></div>
      <div className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]"><AlertTriangle className="h-5 w-5 text-primary" /><p className="mt-3 text-xs font-bold uppercase tracking-wide text-muted-foreground">Open capacity incidents</p><p className="mt-1 font-display text-3xl font-bold text-ink">{summary?.openIncidentCount ?? 0}</p></div>
    </section>

    {status && <p role="status" className="rounded-2xl border border-border bg-white p-4 text-sm text-ink">{status}</p>}

    <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
      <div className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="font-display text-xl font-bold text-ink">Recurring kitchen slot capacity</h2><p className="mt-1 max-w-3xl text-sm text-muted-foreground">Total capacity is what you can prepare in the slot. Subscription capacity is the portion you allow recurring subscriptions to reserve. Lowering a limit below existing commitments does not cancel customers; Craves stops new sales and raises an incident.</p></div><button type="button" disabled={busy} onClick={() => void load()} className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-border px-4 text-sm font-semibold text-ink"><RefreshCw className="h-4 w-4" /> Refresh</button></div>
      <div className="mt-5 grid gap-3 md:grid-cols-3 xl:grid-cols-6">
        <label className="text-sm font-semibold text-ink">Weekday<select value={day} onChange={event => setDay(event.target.value)} className="mt-1 min-h-11 w-full rounded-lg border border-border bg-white px-3">{DAYS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></label>
        <label className="text-sm font-semibold text-ink">Meal slot code<input value={slot} onChange={event => setSlot(event.target.value.toUpperCase())} maxLength={40} placeholder="e.g. your slot code" className="mt-1 min-h-11 w-full rounded-lg border border-border px-3" /></label>
        <label className="text-sm font-semibold text-ink">Total kitchen units<input value={total} onChange={event => setTotal(event.target.value)} type="number" min="0" className="mt-1 min-h-11 w-full rounded-lg border border-border px-3" /></label>
        <label className="text-sm font-semibold text-ink">Subscription units<input value={subscription} onChange={event => setSubscription(event.target.value)} type="number" min="0" className="mt-1 min-h-11 w-full rounded-lg border border-border px-3" /></label>
        <label className="flex min-h-11 items-center gap-2 self-end rounded-lg border border-border px-3 text-sm font-semibold text-ink"><input type="checkbox" checked={salesEnabled} onChange={event => setSalesEnabled(event.target.checked)} /> Accept new subscription sales</label>
        <button type="button" disabled={busy} onClick={() => void saveSlotRule()} className="btn-primary self-end">Save rule</button>
      </div>
      <input value={ruleReason} onChange={event => setRuleReason(event.target.value)} maxLength={1000} placeholder="Required reason for this capacity change" className="mt-3 min-h-11 w-full rounded-lg border border-border px-3 text-sm" />
      <div className="mt-5 overflow-x-auto"><table className="min-w-[850px] w-full text-left text-sm"><thead><tr className="border-b border-border text-muted-foreground"><th className="p-3">Day</th><th className="p-3">Slot</th><th className="p-3">Total</th><th className="p-3">Subscription limit</th><th className="p-3">Reserved</th><th className="p-3">Available</th><th className="p-3">Deficit</th><th className="p-3">Sales</th></tr></thead><tbody>{(summary?.slotRules ?? []).map(rule => <tr key={rule.id} className="border-b border-border/60"><td className="p-3">{DAYS.find(([value]) => value === rule.isoDayOfWeek)?.[1]}</td><td className="p-3 font-bold">{rule.mealSlotCode}</td><td className="p-3">{rule.totalCapacityUnits}</td><td className="p-3">{rule.subscriptionCapacityUnits}</td><td className="p-3">{rule.recurringReservedUnits}</td><td className="p-3 font-bold text-success">{rule.recurringAvailableUnits}</td><td className={`p-3 font-bold ${rule.recurringDeficitUnits > 0 ? "text-error" : "text-muted-foreground"}`}>{rule.recurringDeficitUnits}</td><td className="p-3">{rule.salesEnabled ? "OPEN" : "CLOSED"}</td></tr>)}</tbody></table></div>
    </section>

    <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
      <h2 className="font-display text-xl font-bold text-ink">Date-specific slot override</h2><p className="mt-1 text-sm text-muted-foreground">Use this for a future holiday, reduced kitchen output, extra capacity or a full slot closure. It overrides the normal weekday rule only for that date.</p>
      <div className="mt-5 grid gap-3 md:grid-cols-3 xl:grid-cols-6"><input aria-label="Override date" type="date" min={today} value={date} onChange={event => setDate(event.target.value)} className="min-h-11 rounded-lg border border-border px-3" /><input aria-label="Override meal slot" value={dateSlot} onChange={event => setDateSlot(event.target.value.toUpperCase())} placeholder="Slot code" className="min-h-11 rounded-lg border border-border px-3" /><input aria-label="Override total capacity" type="number" min="0" value={dateTotal} onChange={event => setDateTotal(event.target.value)} placeholder="Total units" className="min-h-11 rounded-lg border border-border px-3" /><input aria-label="Override subscription capacity" type="number" min="0" value={dateSubscription} onChange={event => setDateSubscription(event.target.value)} placeholder="Subscription units" className="min-h-11 rounded-lg border border-border px-3" /><label className="flex min-h-11 items-center gap-2 rounded-lg border border-border px-3 text-sm font-semibold"><input type="checkbox" checked={dateClosed} onChange={event => setDateClosed(event.target.checked)} /> Close slot</label><button type="button" disabled={busy} onClick={() => void saveDateOverride()} className="btn-primary">Save override</button></div>
      <input value={dateReason} onChange={event => setDateReason(event.target.value)} maxLength={1000} placeholder="Required override reason" className="mt-3 min-h-11 w-full rounded-lg border border-border px-3 text-sm" />
      <div className="mt-5 grid gap-3 lg:grid-cols-2">{(summary?.dateOverrides ?? []).map(item => <article key={item.id} className="rounded-xl bg-cream p-4"><div className="flex flex-wrap justify-between gap-2"><strong>{item.serviceDate} · {item.mealSlotCode}</strong><span className={item.closed || item.deficitUnits > 0 ? "font-bold text-error" : "font-bold text-success"}>{item.closed ? "CLOSED" : item.deficitUnits > 0 ? `DEFICIT ${item.deficitUnits}` : "AVAILABLE"}</span></div><p className="mt-2 text-sm text-muted-foreground">Limit {item.subscriptionCapacityUnits} · Held {item.heldUnits} · Committed {item.committedUnits}</p><p className="mt-1 text-xs text-muted-foreground">{item.reason ?? "No reason recorded"}</p></article>)}</div>
    </section>

    <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
      <div className="flex items-start gap-3"><Utensils className="mt-1 h-5 w-5 text-primary" /><div><h2 className="font-display text-xl font-bold text-ink">Optional menu-item capacity</h2><p className="mt-1 text-sm text-muted-foreground">Use an item limit only when a specific dish has a tighter preparation limit than the whole meal slot. If no item rule exists, the slot rule remains authoritative.</p></div></div>
      <div className="mt-5 grid gap-3 md:grid-cols-3 xl:grid-cols-6"><select aria-label="Menu item" value={menuItemId} onChange={event => setMenuItemId(event.target.value)} className="min-h-11 rounded-lg border border-border px-3"><option value="">Choose menu item</option>{menu.map(item => <option key={item.id} value={item.id}>{item.itemName}</option>)}</select><select aria-label="Menu capacity weekday" value={menuDay} onChange={event => setMenuDay(event.target.value)} className="min-h-11 rounded-lg border border-border px-3">{DAYS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><input aria-label="Menu capacity slot" value={menuSlot} onChange={event => setMenuSlot(event.target.value.toUpperCase())} placeholder="Slot code" className="min-h-11 rounded-lg border border-border px-3" /><input aria-label="Menu capacity limit" type="number" min="0" value={menuLimit} onChange={event => setMenuLimit(event.target.value)} placeholder="Item subscription units" className="min-h-11 rounded-lg border border-border px-3" /><label className="flex min-h-11 items-center gap-2 rounded-lg border border-border px-3 text-sm font-semibold"><input type="checkbox" checked={menuSalesEnabled} onChange={event => setMenuSalesEnabled(event.target.checked)} /> Item open</label><button type="button" disabled={busy} onClick={() => void saveMenuRule()} className="btn-primary">Save item rule</button></div>
      <input value={menuReason} onChange={event => setMenuReason(event.target.value)} maxLength={1000} placeholder="Required item-capacity reason" className="mt-3 min-h-11 w-full rounded-lg border border-border px-3 text-sm" />
      <div className="mt-5 grid gap-3 lg:grid-cols-2">{(summary?.menuItemRules ?? []).map(item => <article key={item.id} className="rounded-xl bg-cream p-4"><strong>{menuNames.get(item.menuItemId) ?? item.menuItemId}</strong><p className="mt-1 text-sm text-muted-foreground">{DAYS.find(([value]) => value === item.isoDayOfWeek)?.[1]} · {item.mealSlotCode} · limit {item.maxSubscriptionUnits}</p><p className="mt-1 text-sm">Reserved {item.recurringReservedUnits} · Available {item.recurringAvailableUnits} · Deficit <span className={item.recurringDeficitUnits ? "font-bold text-error" : ""}>{item.recurringDeficitUnits}</span></p></article>)}</div>
    </section>

    <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
      <h2 className="font-display text-xl font-bold text-ink">Optional menu-item date override</h2>
      <div className="mt-5 grid gap-3 md:grid-cols-3 xl:grid-cols-6"><select aria-label="Override menu item" value={menuDateItemId} onChange={event => setMenuDateItemId(event.target.value)} className="min-h-11 rounded-lg border border-border px-3"><option value="">Choose menu item</option>{menu.map(item => <option key={item.id} value={item.id}>{item.itemName}</option>)}</select><input aria-label="Menu override date" type="date" min={today} value={menuDate} onChange={event => setMenuDate(event.target.value)} className="min-h-11 rounded-lg border border-border px-3" /><input aria-label="Menu override slot" value={menuDateSlot} onChange={event => setMenuDateSlot(event.target.value.toUpperCase())} placeholder="Slot code" className="min-h-11 rounded-lg border border-border px-3" /><input aria-label="Menu override limit" type="number" min="0" value={menuDateLimit} onChange={event => setMenuDateLimit(event.target.value)} placeholder="Item units" className="min-h-11 rounded-lg border border-border px-3" /><label className="flex min-h-11 items-center gap-2 rounded-lg border border-border px-3 text-sm font-semibold"><input type="checkbox" checked={menuDateClosed} onChange={event => setMenuDateClosed(event.target.checked)} /> Close item</label><button type="button" disabled={busy} onClick={() => void saveMenuDateOverride()} className="btn-primary">Save item override</button></div>
      <input value={menuDateReason} onChange={event => setMenuDateReason(event.target.value)} maxLength={1000} placeholder="Required item-date override reason" className="mt-3 min-h-11 w-full rounded-lg border border-border px-3 text-sm" />
      <div className="mt-5 grid gap-3 lg:grid-cols-2">{(summary?.menuItemDateOverrides ?? []).map(item => <article key={item.id} className="rounded-xl bg-cream p-4"><strong>{menuNames.get(item.menuItemId) ?? item.menuItemId}</strong><p className="mt-1 text-sm text-muted-foreground">{item.serviceDate} · {item.mealSlotCode} · limit {item.maxSubscriptionUnits}</p><p className="mt-1 text-sm">Held {item.heldUnits} · Committed {item.committedUnits} · Deficit <span className={item.deficitUnits ? "font-bold text-error" : ""}>{item.deficitUnits}</span></p></article>)}</div>
    </section>
  </div>;
}
