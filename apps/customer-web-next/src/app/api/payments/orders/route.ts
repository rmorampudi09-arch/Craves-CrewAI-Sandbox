import { NextRequest, NextResponse } from "next/server";
import { parseIdentity } from "@/lib/auth-contract";
import { parsePaymentCreateInput, parsePaymentSession } from "@/lib/payment-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) {
    return NextResponse.json(
      { error: "ORIGIN_REJECTED", message: "Invalid payment request origin." },
      { status: 403 },
    );
  }

  const input = parsePaymentCreateInput(await request.json().catch(() => null));
  if (!input) {
    return NextResponse.json(
      { error: "INVALID_CHECKOUT_ID", message: "A valid checkout id is required." },
      { status: 400 },
    );
  }

  try {
    const identityResponse = await authenticatedApiFetch(request, "/auth/me");
    const identityBody = (await identityResponse.json().catch(() => null)) as { identity?: unknown } | null;

    if (!identityResponse.ok) {
      return NextResponse.json(
        { error: "SESSION_REQUIRED", message: "Please sign in again." },
        { status: identityResponse.status },
      );
    }

    const identity = parseIdentity(identityBody?.identity);
    if (!identity) {
      return NextResponse.json(
        {
          error: "INVALID_IDENTITY_RESPONSE",
          message: "Customer identity could not be used for payment.",
        },
        { status: 502 },
      );
    }

    const origin = request.nextUrl.origin;
    if (process.env.NODE_ENV === "production" && !origin.startsWith("https://")) {
      return NextResponse.json(
        { error: "HTTPS_REQUIRED", message: "Secure HTTPS is required for payment." },
        { status: 500 },
      );
    }

    const upstream = await authenticatedApiFetch(
      request,
      "/payments/orders",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          checkoutId: input.checkoutId,
          customerName: identity.displayName || "Craves Customer",
          customerEmail: identity.email,
          customerPhone: identity.phoneNumber,
          returnUrl: `${origin}/checkout/${input.checkoutId}/payment`,
        }),
      },
      20_000,
    );

    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) {
      return NextResponse.json(
        {
          error: upstream.status === 401 ? "SESSION_REQUIRED" : "PAYMENT_CREATE_FAILED",
          message:
            upstream.status === 401
              ? "Please sign in again."
              : upstream.status === 400
                ? "Checkout is not ready for payment."
                : "Payment order could not be created.",
        },
        { status: upstream.status },
      );
    }

    const session = parsePaymentSession(body);
    if (!session) {
      return NextResponse.json(
        {
          error: "INVALID_UPSTREAM_RESPONSE",
          message: "Payment response validation failed.",
        },
        { status: 502 },
      );
    }

    return NextResponse.json(session, {
      status: 201,
      headers: { "Cache-Control": "no-store" },
    });
  } catch (error) {
    const timeout = error instanceof Error && error.name === "AbortError";
    return NextResponse.json(
      {
        error:
          error instanceof SessionRequiredError
            ? "SESSION_REQUIRED"
            : timeout
              ? "PAYMENT_TIMEOUT"
              : "PAYMENT_UNAVAILABLE",
        message:
          error instanceof SessionRequiredError
            ? "Please sign in again."
            : "Payment is unavailable right now.",
      },
      { status: error instanceof SessionRequiredError ? 401 : timeout ? 504 : 502 },
    );
  }
}
