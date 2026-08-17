import { NextRequest, NextResponse } from "next/server";
import { isSameOrigin } from "@/lib/request-security";
import { parseSubscriptionPayment } from "@/lib/subscription-payment-contract";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

function providerValue(value: unknown, prefix: string, max = 500): string | null {
  if (typeof value !== "string") return null;
  const result = value.trim();
  return result.startsWith(prefix) && result.length <= max ? result : null;
}

export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ invoiceId: string }> },
) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const { invoiceId } = await params;
  if (!isUuid(invoiceId)) return NextResponse.json({ code: "INVALID_INVOICE_ID" }, { status: 400 });
  const raw = await request.json().catch(() => null) as Record<string, unknown> | null;
  const subscriptionId = typeof raw?.subscriptionId === "string" ? raw.subscriptionId.trim() : "";
  const providerOrderId = providerValue(raw?.providerOrderId, "order_");
  const providerPaymentId = providerValue(raw?.providerPaymentId, "pay_");
  const providerSignature = typeof raw?.providerSignature === "string" && /^[a-f0-9]{64}$/i.test(raw.providerSignature)
    ? raw.providerSignature : null;
  if (!isUuid(subscriptionId) || !providerOrderId || !providerPaymentId || !providerSignature) {
    return NextResponse.json({ code: "INVALID_RAZORPAY_RESPONSE" }, { status: 400 });
  }
  try {
    const upstream = await authenticatedApiFetch(
      request,
      `/subscription-payments/invoices/${encodeURIComponent(invoiceId)}/verify`,
      { method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ providerOrderId, providerPaymentId, providerSignature }) },
      20_000,
    );
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json(
      { code: upstream.status === 401 ? "SESSION_EXPIRED" : "SUBSCRIPTION_PAYMENT_VERIFY_FAILED" },
      { status: upstream.status },
    );
    const payment = parseSubscriptionPayment(body);
    if (!payment || payment.invoiceId !== invoiceId || payment.subscriptionId !== subscriptionId) {
      return NextResponse.json({ code: "INVALID_SUBSCRIPTION_PAYMENT_RESPONSE" }, { status: 502 });
    }
    return NextResponse.json(payment, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    return NextResponse.json({ code: "SUBSCRIPTION_PAYMENT_VERIFY_UNAVAILABLE" }, { status: 503 });
  }
}
