import { NextRequest, NextResponse } from "next/server";
import { parseAdminSubscriptionOperation } from "@/lib/admin-subscription-operation-contract";
import { parseCustomerSubscription } from "@/lib/subscription-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export async function PATCH(request: NextRequest, context: { params: Promise<{ subscriptionId: string }> }) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const { subscriptionId } = await context.params;
  if (!isUuid(subscriptionId)) return NextResponse.json({ code: "INVALID_SUBSCRIPTION_ID" }, { status: 400 });
  const operation = parseAdminSubscriptionOperation(await request.json().catch(() => null));
  if (!operation) return NextResponse.json({ code: "STATUS_AND_REASON_REQUIRED" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/admin/subscriptions/${subscriptionId}/status/${operation.status}`, { method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ reason: operation.reason }) });
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : "SUBSCRIPTION_STATUS_UPDATE_FAILED" }, { status: upstream.status });
    const subscription = parseCustomerSubscription(body);
    return subscription ? NextResponse.json(subscription, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_SUBSCRIPTION_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
    return NextResponse.json({ code: "SUBSCRIPTION_STATUS_UNAVAILABLE" }, { status: 503 });
  }
}
