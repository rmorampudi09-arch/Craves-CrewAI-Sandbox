import { NextRequest, NextResponse } from "next/server";
import { parseSubscriptionPayment } from "@/lib/subscription-payment-contract";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ subscriptionId: string }> },
) {
  const { subscriptionId } = await params;
  if (!isUuid(subscriptionId)) {
    return NextResponse.json({ code: "INVALID_SUBSCRIPTION_ID" }, { status: 400 });
  }

  try {
    const upstream = await authenticatedApiFetch(
      request,
      `/subscription-payments/subscriptions/${encodeURIComponent(subscriptionId)}`,
      {},
      10_000,
    );
    const body = await upstream.json().catch(() => null);
    if (upstream.status === 404) {
      return NextResponse.json({ code: "SUBSCRIPTION_PAYMENT_NOT_READY" }, { status: 404 });
    }
    if (upstream.status === 401 || upstream.status === 403) {
      return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    }
    if (!upstream.ok) {
      return NextResponse.json({ code: "SUBSCRIPTION_PAYMENT_UNAVAILABLE" }, { status: upstream.status });
    }

    const payment = parseSubscriptionPayment(body);
    if (!payment || payment.subscriptionId !== subscriptionId) {
      return NextResponse.json({ code: "INVALID_SUBSCRIPTION_PAYMENT_RESPONSE" }, { status: 502 });
    }
    return NextResponse.json(payment, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    if (error instanceof SessionRequiredError) {
      return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    }
    const timedOut = error instanceof Error && error.name === "AbortError";
    return NextResponse.json(
      { code: timedOut ? "SUBSCRIPTION_PAYMENT_TIMEOUT" : "SUBSCRIPTION_PAYMENT_UNAVAILABLE" },
      { status: timedOut ? 504 : 503 },
    );
  }
}
