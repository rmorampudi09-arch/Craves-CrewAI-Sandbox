import { NextRequest, NextResponse } from "next/server";
import { parseIdentity } from "@/lib/auth-contract";
import { parseCustomerProfile } from "@/lib/profile-contract";
import { isSameOrigin } from "@/lib/request-security";
import { parseSubscriptionPayment } from "@/lib/subscription-payment-contract";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

function httpsReturnUrl(request: NextRequest, subscriptionId: string): string | null {
  const origin = request.headers.get("origin");
  if (!origin?.startsWith("https://")) return null;
  return new URL(`/subscriptions/${subscriptionId}/payment`, origin).toString();
}

export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ invoiceId: string }> },
) {
  if (!isSameOrigin(request)) {
    return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  }

  const { invoiceId } = await params;
  if (!isUuid(invoiceId)) {
    return NextResponse.json({ code: "INVALID_INVOICE_ID" }, { status: 400 });
  }

  const input = await request.json().catch(() => null) as { subscriptionId?: unknown } | null;
  const subscriptionId = typeof input?.subscriptionId === "string" ? input.subscriptionId.trim() : "";
  if (!isUuid(subscriptionId)) {
    return NextResponse.json({ code: "INVALID_SUBSCRIPTION_ID" }, { status: 400 });
  }

  try {
    const identityResponse = await authenticatedApiFetch(request, "/auth/me", {}, 8_000);
    const identityBody = await identityResponse.json().catch(() => null) as { identity?: unknown } | null;
    if (!identityResponse.ok) {
      return NextResponse.json(
        { code: identityResponse.status === 401 ? "SESSION_EXPIRED" : "CUSTOMER_PROFILE_UNAVAILABLE" },
        { status: identityResponse.status },
      );
    }
    const identity = parseIdentity(identityBody?.identity);
    if (!identity) {
      return NextResponse.json({ code: "INVALID_CUSTOMER_PROFILE" }, { status: 502 });
    }

    let customerName = identity.displayName?.trim() ?? "";
    let customerPhone = identity.phoneNumber;
    let customerEmail = identity.email;

    if (!customerName) {
      const profileResponse = await authenticatedApiFetch(request, "/customer/profile", {}, 8_000);
      const profileBody = await profileResponse.json().catch(() => null);
      if (profileResponse.status === 401 || profileResponse.status === 403) {
        return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
      }
      if (profileResponse.ok) {
        const profile = parseCustomerProfile(profileBody);
        if (profile) {
          customerName = `${profile.firstName} ${profile.lastName}`.trim();
          customerPhone = profile.registeredPhoneNumber;
          customerEmail = profile.email ?? identity.email;
        }
      }
    }

    if (!customerName) {
      return NextResponse.json(
        { code: "CUSTOMER_NAME_REQUIRED", message: "Add your name to your Craves profile before starting payment." },
        { status: 409 },
      );
    }

    const upstream = await authenticatedApiFetch(
      request,
      `/subscription-payments/invoices/${encodeURIComponent(invoiceId)}/orders`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          customerName,
          customerPhone,
          customerEmail,
          returnUrl: httpsReturnUrl(request, subscriptionId),
        }),
      },
      20_000,
    );
    const body = await upstream.json().catch(() => null);
    if (upstream.status === 401 || upstream.status === 403) {
      return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    }
    if (!upstream.ok) {
      return NextResponse.json(
        { code: "SUBSCRIPTION_PAYMENT_ORDER_FAILED" },
        { status: upstream.status },
      );
    }

    const payment = parseSubscriptionPayment(body);
    if (!payment || payment.invoiceId !== invoiceId || payment.subscriptionId !== subscriptionId) {
      return NextResponse.json({ code: "INVALID_SUBSCRIPTION_PAYMENT_RESPONSE" }, { status: 502 });
    }
    return NextResponse.json(payment, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    if (error instanceof SessionRequiredError) {
      return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    }
    const timedOut = error instanceof Error && error.name === "AbortError";
    return NextResponse.json(
      { code: timedOut ? "SUBSCRIPTION_PAYMENT_ORDER_TIMEOUT" : "SUBSCRIPTION_PAYMENT_ORDER_UNAVAILABLE" },
      { status: timedOut ? 504 : 503 },
    );
  }
}
