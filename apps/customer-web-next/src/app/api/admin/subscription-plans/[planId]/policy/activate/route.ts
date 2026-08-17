import { NextRequest, NextResponse } from "next/server";
import { parseAdminPolicy } from "@/lib/admin-subscription-runtime-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export async function POST(request: NextRequest, context: { params: Promise<{ planId: string }> }) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const { planId } = await context.params;
  if (!isUuid(planId)) return NextResponse.json({ code: "INVALID_PLAN_ID" }, { status: 400 });
  const raw = await request.json().catch(() => null) as Record<string, unknown> | null;
  const reason = raw && typeof raw.reason === "string" && raw.reason.trim() && raw.reason.trim().length <= 1000 ? raw.reason.trim() : null;
  if (!reason) return NextResponse.json({ code: "ACTIVATION_REASON_REQUIRED" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/admin/subscription-plans/${planId}/policy/activate`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ reason }) });
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : upstream.status === 409 ? "PLAN_POLICY_CONFLICT" : "PLAN_POLICY_ACTIVATION_FAILED" }, { status: upstream.status });
    const policy = parseAdminPolicy(body);
    return policy ? NextResponse.json(policy, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_PLAN_POLICY_RESPONSE" }, { status: 502 });
  } catch (caught) {
    if (caught instanceof SessionRequiredError) return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    return NextResponse.json({ code: "SUBSCRIPTION_UNAVAILABLE" }, { status: 503 });
  }
}
