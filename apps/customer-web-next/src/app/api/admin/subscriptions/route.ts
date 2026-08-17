import { NextRequest, NextResponse } from "next/server";
import { parseAdminSubscriptionPage } from "@/lib/admin-subscription-operation-contract";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

const STATUSES = new Set(["PENDING_PAYMENT", "ACTIVE", "PAUSED", "PAYMENT_FAILED", "EXPIRED", "CANCELLED"]);

export async function GET(request: NextRequest) {
  const status = request.nextUrl.searchParams.get("status")?.trim().toUpperCase() ?? "";
  const planId = request.nextUrl.searchParams.get("planId")?.trim() ?? "";
  const afterCreatedAt = request.nextUrl.searchParams.get("afterCreatedAt")?.trim() ?? "";
  const afterId = request.nextUrl.searchParams.get("afterId")?.trim() ?? "";
  const requestedLimit = Number(request.nextUrl.searchParams.get("limit") ?? "50");
  const limit = Number.isInteger(requestedLimit) ? Math.min(200, Math.max(1, requestedLimit)) : 50;

  if (status && !STATUSES.has(status)) return NextResponse.json({ code: "INVALID_SUBSCRIPTION_STATUS" }, { status: 400 });
  if (planId && !isUuid(planId)) return NextResponse.json({ code: "INVALID_PLAN_ID" }, { status: 400 });
  if ((afterCreatedAt && !afterId) || (!afterCreatedAt && afterId)) return NextResponse.json({ code: "INVALID_CURSOR" }, { status: 400 });
  if (afterId && !isUuid(afterId)) return NextResponse.json({ code: "INVALID_CURSOR" }, { status: 400 });
  if (afterCreatedAt && Number.isNaN(Date.parse(afterCreatedAt))) return NextResponse.json({ code: "INVALID_CURSOR" }, { status: 400 });

  const query = new URLSearchParams({ limit: String(limit) });
  if (status) query.set("status", status);
  if (planId) query.set("planId", planId);
  if (afterCreatedAt) query.set("afterCreatedAt", afterCreatedAt);
  if (afterId) query.set("afterId", afterId);

  try {
    const upstream = await authenticatedApiFetch(request, `/admin/subscriptions?${query.toString()}`);
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : "ADMIN_SUBSCRIPTIONS_FAILED" }, { status: upstream.status });
    const page = parseAdminSubscriptionPage(body);
    return page ? NextResponse.json(page, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_ADMIN_SUBSCRIPTIONS_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    return NextResponse.json({ code: "SUBSCRIPTION_UNAVAILABLE" }, { status: 503 });
  }
}
