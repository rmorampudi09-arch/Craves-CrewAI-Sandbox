import { NextRequest, NextResponse } from "next/server";
import { parseCustomerSubscription } from "@/lib/subscription-contract";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest, context: { params: Promise<{ subscriptionId: string }> }) {
  const { subscriptionId } = await context.params;
  if (!isUuid(subscriptionId)) return NextResponse.json({ code: "INVALID_SUBSCRIPTION_ID" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/subscriptions/${subscriptionId}`);
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : upstream.status === 404 ? "SUBSCRIPTION_NOT_FOUND" : "SUBSCRIPTION_LOOKUP_FAILED" }, { status: upstream.status });
    const subscription = parseCustomerSubscription(body);
    return subscription ? NextResponse.json(subscription, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_SUBSCRIPTION_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
    return NextResponse.json({ code: "SUBSCRIPTION_UNAVAILABLE" }, { status: 503 });
  }
}
