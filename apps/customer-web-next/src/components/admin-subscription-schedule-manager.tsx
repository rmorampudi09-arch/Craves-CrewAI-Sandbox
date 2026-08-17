"use client";

import { useCallback, useEffect, useState } from "react";
import type { AdminSubscriptionPlan } from "@/lib/admin-subscription-plan-contract";
import type { AdminSubscriptionSchedule, AdminSubscriptionScheduleItem } from "@/lib/admin-subscription-runtime-contract";

const WEEKDAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

type ReviewScheduleItem = AdminSubscriptionScheduleItem & {
  menuItemName?: string | null;
  menuItemCategory?: string | null;
  menuItemFoodType?: string | null;
  menuItemPrice?: number | null;
  menuItemCurrency?: string | null;
};

function money(value: number, currency: string): string {
  try { return new Intl.NumberFormat("en-IN", { style: "currency", currency }).format(value); }
  catch { return `${currency} ${value.toFixed(2)}`; }
}

export function AdminSubscriptionScheduleManager({ plan }: { plan: AdminSubscriptionPlan }) {
  const [schedule, setSchedule] = useState<AdminSubscriptionSchedule | null>(null);
  const [message, setMessage] = useState("Loading Chef meal schedule…");

  const load = useCallback(async () => {
    const response = await fetch(`/api/admin/subscription-plans/${plan.id}/schedule`, { cache: "no-store" });
    if (response.status === 404) { setSchedule(null); setMessage("Chef has not saved a meal schedule yet."); return; }
    if (response.status === 401) throw new Error("Administrator session expired.");
    if (response.status === 403) throw new Error("Subscription administrator access is required.");
    if (!response.ok) throw new Error("Chef meal schedule is unavailable.");
    setSchedule(await response.json() as AdminSubscriptionSchedule);
    setMessage("");
  }, [plan.id]);

  useEffect(() => { void load().catch(error => setMessage(error instanceof Error ? error.message : "Chef meal schedule is unavailable.")); }, [load]);

  return <section className="rounded-[24px] bg-white p-5 text-slate-950">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <h4 className="text-lg font-bold">Chef meal schedule</h4>
        <p className="mt-1 text-xs text-slate-500">Review-only. These dish details are snapshotted when the Chef saves the plan, and live availability is checked again during approval.</p>
      </div>
      {schedule && <span className="rounded-full bg-[#FFF8EC] px-3 py-1 text-xs font-bold text-[#6930CA]">{schedule.status} · v{schedule.version}</span>}
    </div>
    {message && <p className="mt-4 rounded-xl bg-[#FFF8EC] p-3 text-sm text-slate-600" role="status">{message}</p>}
    {schedule && <>
      <div className="mt-4 grid gap-3 sm:grid-cols-3">
        <div className="rounded-xl bg-[#FFF8EC] p-3"><p className="text-xs font-bold uppercase text-slate-500">Frequency</p><p className="mt-1 font-bold">{schedule.recurrenceType}</p></div>
        <div className="rounded-xl bg-[#FFF8EC] p-3"><p className="text-xs font-bold uppercase text-slate-500">Timezone</p><p className="mt-1 font-bold">{schedule.timezone}</p></div>
        <div className="rounded-xl bg-[#FFF8EC] p-3"><p className="text-xs font-bold uppercase text-slate-500">Meals</p><p className="mt-1 font-bold">{schedule.items.length}</p></div>
      </div>
      <div className="mt-4 space-y-2">
        {schedule.items.map(rawItem => {
          const item = rawItem as ReviewScheduleItem;
          const day = schedule.recurrenceType === "WEEKLY"
            ? WEEKDAYS[(item.isoDayOfWeek ?? 1) - 1]
            : `Day ${item.dayOfMonth}`;
          const details = [item.menuItemCategory, item.menuItemFoodType?.replaceAll("_", " ")].filter(Boolean).join(" · ");
          return <div key={item.id} className="grid gap-3 rounded-2xl border border-[#eadfd0] p-4 text-sm sm:grid-cols-[1.3fr_1fr_1.8fr_.6fr]">
            <div><p className="text-xs font-bold uppercase text-slate-500">When</p><p className="font-semibold">{day} · {item.serviceTime.slice(0, 5)}</p></div>
            <div><p className="text-xs font-bold uppercase text-slate-500">Meal slot</p><p className="font-semibold">{item.mealSlotCode.replaceAll("_", " ")}</p></div>
            <div>
              <p className="text-xs font-bold uppercase text-slate-500">Dish</p>
              <p className="font-bold text-slate-900">{item.menuItemName ?? "Legacy menu item"}</p>
              {details && <p className="mt-0.5 text-xs text-slate-500">{details}</p>}
              {item.menuItemPrice != null && item.menuItemCurrency && <p className="mt-0.5 text-xs font-semibold text-[#6930CA]">{money(item.menuItemPrice, item.menuItemCurrency)}</p>}
              {!item.menuItemName && <p className="mt-1 truncate font-mono text-[11px] text-slate-400" title={item.menuItemId}>{item.menuItemId}</p>}
            </div>
            <div><p className="text-xs font-bold uppercase text-slate-500">Qty</p><p className="font-semibold">{item.quantity}</p></div>
          </div>;
        })}
      </div>
    </>}
  </section>;
}
