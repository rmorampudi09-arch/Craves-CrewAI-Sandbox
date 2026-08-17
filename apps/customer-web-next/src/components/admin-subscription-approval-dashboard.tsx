"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { CalendarDays, CheckCircle2, CircleAlert, Gauge, ShieldCheck } from "lucide-react";
import type { AdminSubscriptionPlan } from "@/lib/admin-subscription-plan-contract";
import {
  parseAdminSchedule,
  type AdminSubscriptionSchedule,
  type AdminSubscriptionScheduleItem,
} from "@/lib/admin-subscription-runtime-contract";
import {
  parseChefCapacitySummary,
  type ChefCapacityMenuRule,
  type ChefCapacitySlotRule,
  type ChefCapacitySummary,
} from "@/lib/chef-subscription-capacity-contract";

const DAYS = [
  [1, "Monday"],
  [2, "Tuesday"],
  [3, "Wednesday"],
  [4, "Thursday"],
  [5, "Friday"],
  [6, "Saturday"],
  [7, "Sunday"],
] as const;

function money(value: number, currency: string): string {
  try { return new Intl.NumberFormat("en-IN", { style: "currency", currency }).format(value); }
  catch { return `${currency} ${value.toFixed(2)}`; }
}

function dayLabel(day: number): string {
  return DAYS.find(([value]) => value === day)?.[1] ?? `Day ${day}`;
}

type SlotRequirement = {
  day: number;
  slot: string;
  demand: number;
  rule: ChefCapacitySlotRule | null;
  ready: boolean;
  reason: string;
};

type ItemBlocker = {
  day: number;
  slot: string;
  menuItemId: string;
  demand: number;
  rule: ChefCapacityMenuRule;
  reason: string;
};

function groupWeeklySlotDemand(items: AdminSubscriptionScheduleItem[]): Map<string, number> {
  const grouped = new Map<string, number>();
  for (const item of items) {
    if (item.isoDayOfWeek == null) continue;
    const key = `${item.isoDayOfWeek}|${item.mealSlotCode}`;
    grouped.set(key, (grouped.get(key) ?? 0) + item.quantity);
  }
  return grouped;
}

function groupMonthlySlotDemand(items: AdminSubscriptionScheduleItem[]): Map<string, number> {
  const perDate = new Map<string, number>();
  for (const item of items) {
    if (item.dayOfMonth == null) continue;
    const key = `${item.dayOfMonth}|${item.mealSlotCode}`;
    perDate.set(key, (perDate.get(key) ?? 0) + item.quantity);
  }
  const maximumBySlot = new Map<string, number>();
  for (const [key, demand] of perDate) {
    const slot = key.split("|")[1];
    maximumBySlot.set(slot, Math.max(maximumBySlot.get(slot) ?? 0, demand));
  }
  return maximumBySlot;
}

function slotRequirements(schedule: AdminSubscriptionSchedule, capacity: ChefCapacitySummary): SlotRequirement[] {
  const requirements: Array<{ day: number; slot: string; demand: number }> = [];
  if (schedule.recurrenceType === "WEEKLY") {
    for (const [key, demand] of groupWeeklySlotDemand(schedule.items)) {
      const [dayRaw, slot] = key.split("|");
      requirements.push({ day: Number(dayRaw), slot, demand });
    }
  } else {
    for (const [slot, demand] of groupMonthlySlotDemand(schedule.items)) {
      for (const [day] of DAYS) requirements.push({ day, slot, demand });
    }
  }

  return requirements
    .map(requirement => {
      const rule = capacity.slotRules.find(item => item.isoDayOfWeek === requirement.day && item.mealSlotCode === requirement.slot) ?? null;
      if (!rule) return { ...requirement, rule, ready: false, reason: "Missing capacity rule" };
      if (!rule.salesEnabled) return { ...requirement, rule, ready: false, reason: "Subscription sales closed" };
      if (rule.recurringDeficitUnits > 0) return { ...requirement, rule, ready: false, reason: `${rule.recurringDeficitUnits} unit deficit` };
      if (rule.recurringAvailableUnits < requirement.demand) {
        return { ...requirement, rule, ready: false, reason: `Needs ${requirement.demand}; only ${rule.recurringAvailableUnits} free` };
      }
      return { ...requirement, rule, ready: true, reason: `${rule.recurringAvailableUnits} free; needs ${requirement.demand}` };
    })
    .sort((a, b) => a.slot.localeCompare(b.slot) || a.day - b.day);
}

function itemBlockers(schedule: AdminSubscriptionSchedule, capacity: ChefCapacitySummary): ItemBlocker[] {
  const blockers: ItemBlocker[] = [];
  if (schedule.recurrenceType === "WEEKLY") {
    const grouped = new Map<string, number>();
    for (const item of schedule.items) {
      if (item.isoDayOfWeek == null) continue;
      const key = `${item.menuItemId}|${item.isoDayOfWeek}|${item.mealSlotCode}`;
      grouped.set(key, (grouped.get(key) ?? 0) + item.quantity);
    }
    for (const [key, demand] of grouped) {
      const [menuItemId, dayRaw, slot] = key.split("|");
      const day = Number(dayRaw);
      const rule = capacity.menuItemRules.find(item => item.menuItemId === menuItemId && item.isoDayOfWeek === day && item.mealSlotCode === slot);
      if (!rule) continue;
      if (!rule.salesEnabled || rule.recurringAvailableUnits < demand || rule.recurringDeficitUnits > 0) {
        blockers.push({ day, slot, menuItemId, demand, rule, reason: !rule.salesEnabled ? "Item sales closed" : `Needs ${demand}; ${rule.recurringAvailableUnits} free` });
      }
    }
    return blockers;
  }

  const perDate = new Map<string, number>();
  for (const item of schedule.items) {
    if (item.dayOfMonth == null) continue;
    const key = `${item.dayOfMonth}|${item.menuItemId}|${item.mealSlotCode}`;
    perDate.set(key, (perDate.get(key) ?? 0) + item.quantity);
  }
  const maximum = new Map<string, number>();
  for (const [key, demand] of perDate) {
    const [, menuItemId, slot] = key.split("|");
    const itemSlot = `${menuItemId}|${slot}`;
    maximum.set(itemSlot, Math.max(maximum.get(itemSlot) ?? 0, demand));
  }
  for (const [itemSlot, demand] of maximum) {
    const [menuItemId, slot] = itemSlot.split("|");
    for (const [day] of DAYS) {
      const rule = capacity.menuItemRules.find(item => item.menuItemId === menuItemId && item.isoDayOfWeek === day && item.mealSlotCode === slot);
      if (!rule) continue;
      if (!rule.salesEnabled || rule.recurringAvailableUnits < demand || rule.recurringDeficitUnits > 0) {
        blockers.push({ day, slot, menuItemId, demand, rule, reason: !rule.salesEnabled ? "Item sales closed" : `Needs ${demand}; ${rule.recurringAvailableUnits} free` });
      }
    }
  }
  return blockers;
}

export function AdminSubscriptionApprovalDashboard({ plan }: { plan: AdminSubscriptionPlan }) {
  const [schedule, setSchedule] = useState<AdminSubscriptionSchedule | null>(null);
  const [capacity, setCapacity] = useState<ChefCapacitySummary | null>(null);
  const [message, setMessage] = useState("Checking approval readiness…");

  const load = useCallback(async () => {
    if (!plan.chefIdentityId) {
      setSchedule(null);
      setCapacity(null);
      setMessage("This plan has no Chef identity assigned.");
      return;
    }
    const [scheduleResponse, capacityResponse] = await Promise.all([
      fetch(`/api/admin/subscription-plans/${plan.id}/schedule`, { cache: "no-store" }),
      fetch(`/api/admin/subscription-capacity/chefs/${plan.chefIdentityId}`, { cache: "no-store" }),
    ]);
    const [scheduleRaw, capacityRaw] = await Promise.all([
      scheduleResponse.json().catch(() => null),
      capacityResponse.json().catch(() => null),
    ]);
    if (scheduleResponse.status === 404) {
      setSchedule(null);
    } else {
      if (!scheduleResponse.ok) throw new Error("Chef meal schedule could not be checked.");
      const parsedSchedule = parseAdminSchedule(scheduleRaw);
      if (!parsedSchedule) throw new Error("Craves returned an invalid meal schedule.");
      setSchedule(parsedSchedule);
    }
    if (!capacityResponse.ok) throw new Error("Chef subscription capacity could not be checked.");
    const parsedCapacity = parseChefCapacitySummary(capacityRaw);
    if (!parsedCapacity) throw new Error("Craves returned an invalid capacity summary.");
    setCapacity(parsedCapacity);
    setMessage("");
  }, [plan.id, plan.chefIdentityId]);

  useEffect(() => {
    void load().catch(error => setMessage(error instanceof Error ? error.message : "Approval readiness could not be checked."));
  }, [load]);

  const requirements = useMemo(
    () => schedule && capacity ? slotRequirements(schedule, capacity) : [],
    [schedule, capacity],
  );
  const itemRestrictions = useMemo(
    () => schedule && capacity ? itemBlockers(schedule, capacity) : [],
    [schedule, capacity],
  );
  const missing = requirements.filter(item => !item.ready);
  const distinctSlots = schedule ? [...new Set(schedule.items.map(item => item.mealSlotCode))].sort() : [];
  const minAvailable = requirements.length
    ? Math.min(...requirements.map(item => item.rule?.recurringAvailableUnits ?? 0))
    : 0;
  const ready = Boolean(
    schedule && capacity && requirements.length > 0 && missing.length === 0 && itemRestrictions.length === 0 && !capacity.adminSalesFrozen,
  );

  return (
    <section className="rounded-2xl border border-border bg-white p-5 text-slate-950">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#6930CA]">Approval readiness</p>
          <h4 className="mt-1 text-lg font-bold">Meal plan safety dashboard</h4>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-500">This is read-only. It combines the submitted schedule with the Chef’s current subscription-capacity rules so the reviewer can see missing setup before approving.</p>
        </div>
        <button type="button" onClick={() => void load()} className="rounded-xl border border-[#d9cdbd] px-3 py-2 text-xs font-bold text-slate-700">Refresh checks</button>
      </div>

      {message && <p className="mt-4 rounded-xl bg-[#FFF8EC] p-3 text-sm text-slate-700" role="status">{message}</p>}

      <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <div className="rounded-xl bg-[#FFF8EC] p-4">
          <CalendarDays className="h-5 w-5 text-[#6930CA]" />
          <p className="mt-2 text-xs font-bold uppercase text-slate-500">Plan</p>
          <p className="mt-1 font-bold">{plan.billingPeriod} · {money(plan.amount, plan.currency)}</p>
          <p className="mt-1 text-xs text-slate-500">Status: {plan.status}</p>
        </div>
        <div className="rounded-xl bg-[#FFF8EC] p-4">
          <ShieldCheck className="h-5 w-5 text-[#6930CA]" />
          <p className="mt-2 text-xs font-bold uppercase text-slate-500">Schedule</p>
          <p className="mt-1 font-bold">{schedule ? `${schedule.items.length} meal row(s)` : "Missing"}</p>
          <p className="mt-1 text-xs text-slate-500">{schedule ? `${distinctSlots.join(", ")} · ${schedule.timezone} · ${schedule.generationLeadHours}h lead` : "Chef must save a schedule."}</p>
        </div>
        <div className="rounded-xl bg-[#FFF8EC] p-4">
          <Gauge className="h-5 w-5 text-[#6930CA]" />
          <p className="mt-2 text-xs font-bold uppercase text-slate-500">Required slot rules</p>
          <p className="mt-1 font-bold">{requirements.length - missing.length}/{requirements.length || 0} ready</p>
          <p className="mt-1 text-xs text-slate-500">Minimum free capacity: {minAvailable}</p>
        </div>
        <div className={`rounded-xl p-4 ${ready ? "bg-emerald-50" : "bg-amber-50"}`}>
          {ready ? <CheckCircle2 className="h-5 w-5 text-emerald-700" /> : <CircleAlert className="h-5 w-5 text-amber-700" />}
          <p className="mt-2 text-xs font-bold uppercase text-slate-500">Decision check</p>
          <p className={`mt-1 font-bold ${ready ? "text-emerald-800" : "text-amber-800"}`}>{ready ? "Ready for approval" : "Action required"}</p>
          <p className="mt-1 text-xs text-slate-600">{capacity?.adminSalesFrozen ? "Sales frozen by operations" : `${capacity?.openIncidentCount ?? 0} open capacity incident(s)`}</p>
        </div>
      </div>

      {schedule?.recurrenceType === "MONTHLY" && <p className="mt-4 rounded-xl border border-[#eadfd0] bg-[#FFF8EC] p-3 text-xs leading-5 text-slate-600">
        <strong className="text-slate-900">Why all 7 weekdays?</strong> A monthly plan is tied to a date of the month. That date can fall on a different weekday in future months, so each used meal slot needs capacity on Monday through Sunday.
      </p>}

      {requirements.length > 0 && <div className="mt-4 overflow-x-auto">
        <table className="min-w-[900px] w-full text-left text-xs">
          <thead><tr className="border-b border-[#eadfd0] text-slate-500"><th className="p-3">Weekday</th><th className="p-3">Meal slot</th><th className="p-3">Plan needs</th><th className="p-3">Configured limit</th><th className="p-3">Reserved</th><th className="p-3">Available</th><th className="p-3">Sales</th><th className="p-3">Result</th></tr></thead>
          <tbody>{requirements.map(item => <tr key={`${item.day}-${item.slot}`} className="border-b border-[#f0e7dc]">
            <td className="p-3 font-semibold">{dayLabel(item.day)}</td>
            <td className="p-3 font-bold">{item.slot}</td>
            <td className="p-3">{item.demand}</td>
            <td className="p-3">{item.rule?.subscriptionCapacityUnits ?? "—"}</td>
            <td className="p-3">{item.rule?.recurringReservedUnits ?? "—"}</td>
            <td className="p-3">{item.rule?.recurringAvailableUnits ?? "—"}</td>
            <td className="p-3">{item.rule ? item.rule.salesEnabled ? "OPEN" : "CLOSED" : "—"}</td>
            <td className={`p-3 font-bold ${item.ready ? "text-emerald-700" : "text-amber-800"}`}>{item.ready ? "READY" : item.reason}</td>
          </tr>)}</tbody>
        </table>
      </div>}

      {missing.length > 0 && <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4">
        <p className="text-sm font-bold text-amber-900">Chef action required before approval</p>
        <div className="mt-2 flex flex-wrap gap-2">{missing.map(item => <span key={`${item.day}-${item.slot}`} className="rounded-full bg-white px-3 py-1 text-xs font-semibold text-amber-900">{dayLabel(item.day)} · {item.slot}: {item.reason}</span>)}</div>
      </div>}

      {itemRestrictions.length > 0 && <div className="mt-4 rounded-xl border border-amber-200 bg-amber-50 p-4">
        <p className="text-sm font-bold text-amber-900">Menu-item capacity restrictions</p>
        <div className="mt-2 space-y-1 text-xs text-amber-900">{itemRestrictions.map((item, index) => <p key={`${item.menuItemId}-${item.day}-${item.slot}-${index}`}>{dayLabel(item.day)} · {item.slot} · item {item.menuItemId.slice(0, 8)}… — {item.reason}</p>)}</div>
      </div>}

      {ready && <div className="mt-4 flex items-start gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900">
        <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0" />
        <div><strong>Capacity check looks ready.</strong><p className="mt-1 text-xs leading-5">Admin can proceed with review. The backend will still revalidate the Chef’s dishes and capacity transactionally at approval time.</p></div>
      </div>}
    </section>
  );
}
