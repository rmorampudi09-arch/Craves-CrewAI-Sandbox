import { NextRequest, NextResponse } from "next/server";
import { parseAdminReadiness } from "@/lib/admin-subscription-runtime-contract";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest, context: { params: Promise<{ planId: string }> }) {
  const { planId } = await context.params;
  if (!isUuid(planId)) return NextResponse.json({ code: "INVALID_PLAN_ID" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/admin/subscription-plans/${planId}/readiness`);
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : upstream.status === 404 ? "PLAN_NOT_FOUND" : "PLAN_READINESS_FAILED" }, { status: upstream.status });
    const readiness = parseAdminReadiness(body);
    return readiness ? NextResponse.json(readiness, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_PLAN_READINESS_RESPONSE" }, { status: 502 });
  } catch (caught) {
    if (caught instanceof SessionRequiredError) return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    return NextResponse.json({ code: "SUBSCRIPTION_UNAVAILABLE" }, { status: 503 });
  }
}
