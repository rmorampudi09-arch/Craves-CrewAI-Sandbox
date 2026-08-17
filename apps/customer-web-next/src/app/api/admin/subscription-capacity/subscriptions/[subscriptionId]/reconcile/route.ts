import { NextRequest, NextResponse } from "next/server";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export async function POST(request: NextRequest, context: { params: Promise<{ subscriptionId: string }> }) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const { subscriptionId } = await context.params;
  if (!isUuid(subscriptionId)) return NextResponse.json({ code: "INVALID_SUBSCRIPTION_ID" }, { status: 400 });
  const raw = await request.json().catch(() => null) as Record<string, unknown> | null;
  const reason = typeof raw?.reason === "string" && raw.reason.trim().length > 0 && raw.reason.trim().length <= 1000 ? raw.reason.trim() : null;
  if (!reason) return NextResponse.json({ code: "RECONCILIATION_REASON_REQUIRED" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/admin/subscription-capacity/subscriptions/${subscriptionId}/reconcile`, {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ reason }),
    });
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : upstream.status === 404 ? "SUBSCRIPTION_NOT_FOUND" : upstream.status === 409 ? "CAPACITY_RECONCILIATION_CONFLICT" : "CAPACITY_RECONCILIATION_FAILED" }, { status: upstream.status });
    return new NextResponse(null, { status: 204 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    return NextResponse.json({ code: "CAPACITY_UNAVAILABLE" }, { status: 503 });
  }
}
