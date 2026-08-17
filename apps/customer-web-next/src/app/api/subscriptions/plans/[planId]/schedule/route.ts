import { NextResponse } from "next/server";
import { parsePublicSubscriptionSchedule } from "@/lib/subscription-schedule-contract";
import { apiBaseUrl, isUuid } from "@/lib/server-api";

export const dynamic = "force-dynamic";

function parseMenuItemName(value: unknown, menuItemId: string): string | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  if (raw.id !== menuItemId || typeof raw.itemName !== "string") return null;
  const name = raw.itemName.trim();
  return name && name.length <= 200 ? name : null;
}

export async function GET(_request: Request, context: { params: Promise<{ planId: string }> }) {
  const { planId } = await context.params;
  if (!isUuid(planId)) return NextResponse.json({ code: "INVALID_PLAN_ID" }, { status: 400 });
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10_000);
  try {
    const upstream = await fetch(`${apiBaseUrl()}/subscriptions/plans/${planId}/schedule`, {
      headers: { Accept: "application/json" }, cache: "no-store", signal: controller.signal,
    });
    if (!upstream.ok) return NextResponse.json({ code: "SUBSCRIPTION_SCHEDULE_UNAVAILABLE" }, { status: upstream.status });
    const schedule = parsePublicSubscriptionSchedule(await upstream.json().catch(() => null));
    if (!schedule) return NextResponse.json({ code: "INVALID_SUBSCRIPTION_SCHEDULE_RESPONSE" }, { status: 502 });

    const uniqueMenuIds = [...new Set(schedule.items.map(item => item.menuItemId))];
    const names = new Map<string, string>();
    await Promise.all(uniqueMenuIds.map(async menuItemId => {
      try {
        const response = await fetch(`${apiBaseUrl()}/catalog/menu-items/${menuItemId}`, {
          headers: { Accept: "application/json" }, cache: "no-store", signal: controller.signal,
        });
        if (!response.ok) return;
        const name = parseMenuItemName(await response.json().catch(() => null), menuItemId);
        if (name) names.set(menuItemId, name);
      } catch {
        // Schedule remains usable even when optional display enrichment is temporarily unavailable.
      }
    }));

    return NextResponse.json({
      ...schedule,
      items: schedule.items.map(item => ({ ...item, itemName: names.get(item.menuItemId) ?? null })),
    }, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    const timedOut = error instanceof Error && error.name === "AbortError";
    return NextResponse.json({ code: timedOut ? "SUBSCRIPTION_SCHEDULE_TIMEOUT" : "SUBSCRIPTION_SCHEDULE_UNAVAILABLE" }, { status: timedOut ? 504 : 503 });
  } finally {
    clearTimeout(timeout);
  }
}
