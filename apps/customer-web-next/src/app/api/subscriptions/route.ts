import { NextRequest, NextResponse } from "next/server";
import { parseCreateSubscriptionInput, parseCustomerSubscription, parseCustomerSubscriptions } from "@/lib/subscription-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

const IDEMPOTENCY_KEY = /^[A-Za-z0-9._:-]{8,128}$/;
const SUBSCRIPTION_UPSTREAM_TIMEOUT_MS = 30_000;

function errorResponse(status: number) {
  if (status === 401) return NextResponse.json({ code: "SESSION_EXPIRED" }, { status });
  if (status === 403) return NextResponse.json({ code: "SUBSCRIPTION_ACCESS_DENIED" }, { status });
  if (status === 409) return NextResponse.json({ code: "SUBSCRIPTION_CONFLICT" }, { status });
  return NextResponse.json({ code: "SUBSCRIPTION_REQUEST_FAILED" }, { status });
}

export async function GET(request: NextRequest) {
  try {
    const upstream = await authenticatedApiFetch(request, "/subscriptions", {}, SUBSCRIPTION_UPSTREAM_TIMEOUT_MS);
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return errorResponse(upstream.status);
    const subscriptions = parseCustomerSubscriptions(body);
    return subscriptions ? NextResponse.json(subscriptions, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_SUBSCRIPTION_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return errorResponse(401);
    return NextResponse.json({ code: "SUBSCRIPTIONS_UNAVAILABLE" }, { status: 503 });
  }
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const input = parseCreateSubscriptionInput(await request.json().catch(() => null));
  if (!input) return NextResponse.json({ code: "INVALID_SUBSCRIPTION_INPUT" }, { status: 400 });
  const idempotencyKey = request.headers.get("Idempotency-Key")?.trim() ?? "";
  if (!IDEMPOTENCY_KEY.test(idempotencyKey)) {
    return NextResponse.json({ code: "INVALID_IDEMPOTENCY_KEY" }, { status: 400 });
  }
  try {
    const upstream = await authenticatedApiFetch(request, "/subscriptions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey,
      },
      body: JSON.stringify(input),
    }, SUBSCRIPTION_UPSTREAM_TIMEOUT_MS);
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return errorResponse(upstream.status);
    const subscription = parseCustomerSubscription(body);
    return subscription ? NextResponse.json(subscription, { status: 201, headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_SUBSCRIPTION_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return errorResponse(401);
    return NextResponse.json({ code: "SUBSCRIPTION_UNAVAILABLE" }, { status: 503 });
  }
}
