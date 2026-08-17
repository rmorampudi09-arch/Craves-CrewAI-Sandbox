import { NextRequest, NextResponse } from "next/server";
import { parseCustomerSubscription } from "@/lib/subscription-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

const DATE = /^\d{4}-\d{2}-\d{2}$/;

export async function PATCH(request: NextRequest, context: { params: Promise<{ subscriptionId: string }> }) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const { subscriptionId } = await context.params;
  if (!isUuid(subscriptionId)) return NextResponse.json({ code: "INVALID_SUBSCRIPTION_ID" }, { status: 400 });
  const raw = await request.json().catch(() => null) as Record<string, unknown> | null;
  const resumeDate = raw && typeof raw.resumeDate === "string" && DATE.test(raw.resumeDate) ? raw.resumeDate : null;
  const reason = raw?.reason == null || raw.reason === "" ? null : typeof raw.reason === "string" && raw.reason.trim().length <= 1000 ? raw.reason.trim() : undefined;
  if (!resumeDate || reason === undefined) return NextResponse.json({ code: "INVALID_RESUME_INPUT" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/subscriptions/${subscriptionId}/resume`, {
      method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ resumeDate, reason }),
    });
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 409 ? "SUBSCRIPTION_RESUME_CONFLICT" : "SUBSCRIPTION_RESUME_FAILED" }, { status: upstream.status });
    const subscription = parseCustomerSubscription(body);
    return subscription ? NextResponse.json(subscription, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_SUBSCRIPTION_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    return NextResponse.json({ code: "SUBSCRIPTION_UNAVAILABLE" }, { status: 503 });
  }
}
