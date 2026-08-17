import { NextRequest, NextResponse } from "next/server";
import { parseAdminSubscriptionPlans } from "@/lib/admin-subscription-plan-contract";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

function errorResponse(status: number) {
  return NextResponse.json({ code: status === 401 ? "SESSION_EXPIRED" : status === 403 ? "ADMIN_ACCESS_REQUIRED" : "ADMIN_PLAN_REQUEST_FAILED" }, { status });
}

export async function GET(request: NextRequest) {
  try {
    const upstream = await authenticatedApiFetch(request, "/admin/subscription-plans");
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return errorResponse(upstream.status);
    const plans = parseAdminSubscriptionPlans(body);
    return plans ? NextResponse.json(plans, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_PLAN_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return errorResponse(401);
    return NextResponse.json({ code: "ADMIN_PLANS_UNAVAILABLE" }, { status: 503 });
  }
}
