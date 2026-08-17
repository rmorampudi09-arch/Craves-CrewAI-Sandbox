import { NextRequest, NextResponse } from "next/server";
import { parseChefMealPlan } from "@/lib/chef-subscription-plan-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";
type Context = { params: Promise<{ planId: string }> };

export async function POST(request: NextRequest, context: Context) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const { planId } = await context.params;
  if (!isUuid(planId)) return NextResponse.json({ code: "INVALID_PLAN_ID" }, { status: 400 });
  const raw = await request.json().catch(() => null);
  if (!raw || typeof raw !== "object") return NextResponse.json({ code: "INVALID_REVIEW_INPUT" }, { status: 400 });
  const decision = typeof (raw as Record<string, unknown>).decision === "string" ? String((raw as Record<string, unknown>).decision).trim().toUpperCase() : "";
  const reason = typeof (raw as Record<string, unknown>).reason === "string" ? String((raw as Record<string, unknown>).reason).trim() : "";
  if (!new Set(["APPROVE", "REJECT"]).has(decision) || reason.length < 3 || reason.length > 1000) return NextResponse.json({ code: "INVALID_REVIEW_INPUT" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/admin/subscription-plans/${planId}/review`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ decision, reason }),
    });
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "SUBSCRIPTION_ADMIN_REQUIRED" : upstream.status === 409 ? "PLAN_REVIEW_NOT_READY" : "PLAN_REVIEW_FAILED", details: body }, { status: upstream.status });
    const plan = parseChefMealPlan(body);
    return plan ? NextResponse.json(plan, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_PLAN_REVIEW_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    return NextResponse.json({ code: "PLAN_REVIEW_UNAVAILABLE" }, { status: 503 });
  }
}
