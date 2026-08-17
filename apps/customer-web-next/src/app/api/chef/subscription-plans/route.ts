import { NextRequest, NextResponse } from "next/server";
import { parseChefMealPlan, parseChefMealPlanInput, parseChefMealPlans } from "@/lib/chef-subscription-plan-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

function failure(status: number) {
  return NextResponse.json({ code: status === 401 ? "SESSION_EXPIRED" : status === 403 ? "CHEF_ACCESS_REQUIRED" : "CHEF_MEAL_PLAN_REQUEST_FAILED" }, { status });
}

export async function GET(request: NextRequest) {
  try {
    const upstream = await authenticatedApiFetch(request, "/chef/subscription-plans");
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return failure(upstream.status);
    const plans = parseChefMealPlans(body);
    return plans ? NextResponse.json(plans, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_CHEF_MEAL_PLANS_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return failure(401);
    return NextResponse.json({ code: "CHEF_MEAL_PLANS_UNAVAILABLE" }, { status: 503 });
  }
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const input = parseChefMealPlanInput(await request.json().catch(() => null));
  if (!input) return NextResponse.json({ code: "INVALID_CHEF_MEAL_PLAN_INPUT" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, "/chef/subscription-plans", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return failure(upstream.status);
    const plan = parseChefMealPlan(body);
    return plan ? NextResponse.json(plan, { status: 201, headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_CHEF_MEAL_PLAN_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return failure(401);
    return NextResponse.json({ code: "CHEF_MEAL_PLAN_UNAVAILABLE" }, { status: 503 });
  }
}
