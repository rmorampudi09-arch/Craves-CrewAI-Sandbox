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
  const raw = await request.json().catch(() => ({}));
  const note = raw && typeof raw === "object" && typeof (raw as Record<string, unknown>).note === "string"
    ? String((raw as Record<string, unknown>).note).trim().slice(0, 1000)
    : null;
  try {
    const upstream = await authenticatedApiFetch(request, `/chef/subscription-plans/${planId}/submit`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ note }),
    });
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "CHEF_ACCESS_REQUIRED" : upstream.status === 409 ? "MEAL_PLAN_NOT_READY" : "MEAL_PLAN_SUBMIT_FAILED", details: body }, { status: upstream.status });
    const plan = parseChefMealPlan(body);
    return plan ? NextResponse.json(plan, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_CHEF_MEAL_PLAN_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    return NextResponse.json({ code: "MEAL_PLAN_SUBMIT_UNAVAILABLE" }, { status: 503 });
  }
}
