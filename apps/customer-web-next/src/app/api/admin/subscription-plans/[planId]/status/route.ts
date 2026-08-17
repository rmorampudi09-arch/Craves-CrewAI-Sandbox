import { NextRequest, NextResponse } from "next/server";
import { parseAdminPlanStatus, parseAdminSubscriptionPlan } from "@/lib/admin-subscription-plan-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export async function PATCH(request: NextRequest, context: { params: Promise<{ planId: string }> }) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const { planId } = await context.params;
  if (!isUuid(planId)) return NextResponse.json({ code: "INVALID_PLAN_ID" }, { status: 400 });
  const input = parseAdminPlanStatus(await request.json().catch(() => null));
  if (!input || input.status !== "INACTIVE") {
    return NextResponse.json({ code: "PLAN_REVIEW_REQUIRED", message: "Admin can only deactivate here; approval must use the review action." }, { status: 400 });
  }
  try {
    const upstream = await authenticatedApiFetch(request, `/admin/subscription-plans/${planId}/status`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) });
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : "PLAN_STATUS_UPDATE_FAILED" }, { status: upstream.status });
    const plan = parseAdminSubscriptionPlan(body);
    return plan ? NextResponse.json(plan, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_PLAN_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
    return NextResponse.json({ code: "PLAN_STATUS_UNAVAILABLE" }, { status: 503 });
  }
}
