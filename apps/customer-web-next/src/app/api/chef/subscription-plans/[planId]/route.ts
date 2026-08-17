import { NextRequest, NextResponse } from "next/server";
import { parseChefMealPlan, parseChefMealPlanInput } from "@/lib/chef-subscription-plan-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";
type Context = { params: Promise<{ planId: string }> };

function failure(status: number) {
  return NextResponse.json({ code: status === 401 ? "SESSION_EXPIRED" : status === 403 ? "CHEF_ACCESS_REQUIRED" : status === 404 ? "MEAL_PLAN_NOT_FOUND" : "CHEF_MEAL_PLAN_REQUEST_FAILED" }, { status });
}

export async function GET(request: NextRequest, context: Context) {
  const { planId } = await context.params;
  if (!isUuid(planId)) return NextResponse.json({ code: "INVALID_PLAN_ID" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/chef/subscription-plans/${planId}`);
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return failure(upstream.status);
    const plan = parseChefMealPlan(body);
    return plan ? NextResponse.json(plan, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_CHEF_MEAL_PLAN_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return failure(401);
    return NextResponse.json({ code: "CHEF_MEAL_PLAN_UNAVAILABLE" }, { status: 503 });
  }
}

export async function PUT(request: NextRequest, context: Context) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const { planId } = await context.params;
  if (!isUuid(planId)) return NextResponse.json({ code: "INVALID_PLAN_ID" }, { status: 400 });
  const input = parseChefMealPlanInput(await request.json().catch(() => null));
  if (!input) return NextResponse.json({ code: "INVALID_CHEF_MEAL_PLAN_INPUT" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/chef/subscription-plans/${planId}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return failure(upstream.status);
    const plan = parseChefMealPlan(body);
    return plan ? NextResponse.json(plan, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_CHEF_MEAL_PLAN_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return failure(401);
    return NextResponse.json({ code: "CHEF_MEAL_PLAN_UNAVAILABLE" }, { status: 503 });
  }
}
