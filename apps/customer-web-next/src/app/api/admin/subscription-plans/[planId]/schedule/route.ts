import { NextRequest, NextResponse } from "next/server";
import { parseAdminSchedule } from "@/lib/admin-subscription-runtime-contract";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

type MealSnapshot = {
  menuItemName: string | null;
  menuItemCategory: string | null;
  menuItemFoodType: string | null;
  menuItemPrice: number | null;
  menuItemCurrency: string | null;
};

function error(status: number) {
  return NextResponse.json({ code: status === 401 ? "SESSION_EXPIRED" : status === 403 ? "ADMIN_ACCESS_REQUIRED" : status === 404 ? "PLAN_SCHEDULE_NOT_FOUND" : "PLAN_SCHEDULE_FAILED" }, { status });
}

function optionalString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function snapshotMap(body: unknown): Map<string, MealSnapshot> {
  const snapshots = new Map<string, MealSnapshot>();
  if (!body || typeof body !== "object") return snapshots;
  const items = (body as Record<string, unknown>).items;
  if (!Array.isArray(items)) return snapshots;
  for (const value of items) {
    if (!value || typeof value !== "object") continue;
    const raw = value as Record<string, unknown>;
    const id = optionalString(raw.id);
    if (!id) continue;
    const price = raw.menuItemPrice == null ? null : Number(raw.menuItemPrice);
    snapshots.set(id, {
      menuItemName: optionalString(raw.menuItemName),
      menuItemCategory: optionalString(raw.menuItemCategory),
      menuItemFoodType: optionalString(raw.menuItemFoodType),
      menuItemPrice: price != null && Number.isFinite(price) && price >= 0 ? price : null,
      menuItemCurrency: optionalString(raw.menuItemCurrency)?.toUpperCase() ?? null,
    });
  }
  return snapshots;
}

export async function GET(request: NextRequest, context: { params: Promise<{ planId: string }> }) {
  const { planId } = await context.params;
  if (!isUuid(planId)) return NextResponse.json({ code: "INVALID_PLAN_ID" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/admin/subscription-plans/${planId}/schedule`);
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return error(upstream.status);
    const schedule = parseAdminSchedule(body);
    if (!schedule) return NextResponse.json({ code: "INVALID_PLAN_SCHEDULE_RESPONSE" }, { status: 502 });
    const snapshots = snapshotMap(body);
    return NextResponse.json({
      ...schedule,
      items: schedule.items.map(item => ({ ...item, ...(snapshots.get(item.id) ?? {}) })),
    }, { headers: { "Cache-Control": "no-store" } });
  } catch (caught) {
    if (caught instanceof SessionRequiredError) return error(401);
    return NextResponse.json({ code: "SUBSCRIPTION_UNAVAILABLE" }, { status: 503 });
  }
}
