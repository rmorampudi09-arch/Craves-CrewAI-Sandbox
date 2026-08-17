import { NextRequest, NextResponse } from "next/server";
import {
  isCanonicalUuid,
  parseChefOrderResponse,
} from "@/lib/chef-order-contract";
import {
  authenticatedApiFetch,
  SessionRequiredError,
} from "@/lib/server-api";

export const dynamic = "force-dynamic";

function failure(status: number, code: string, message: string) {
  return NextResponse.json({ code, message }, { status });
}

export async function GET(
  request: NextRequest,
  context: { params: Promise<{ orderId: string }> },
) {
  const { orderId } = await context.params;
  if (!isCanonicalUuid(orderId)) {
    return failure(400, "INVALID_ORDER_ID", "A valid order ID is required.");
  }

  try {
    const upstream = await authenticatedApiFetch(
      request,
      `/chef/orders/${encodeURIComponent(orderId)}`,
    );
    const raw = await upstream.json().catch(() => null);
    if (!upstream.ok) {
      return failure(
        upstream.status,
        upstream.status === 401
          ? "SESSION_EXPIRED"
          : upstream.status === 403 || upstream.status === 404
            ? "CHEF_ORDER_NOT_FOUND"
            : "CHEF_ORDER_REQUEST_FAILED",
        upstream.status === 403 || upstream.status === 404
          ? "This order is not available for your approved chef identity."
          : "Chef order is temporarily unavailable.",
      );
    }
    const order = parseChefOrderResponse(raw);
    if (!order || order.id.toLowerCase() !== orderId.toLowerCase()) {
      return failure(
        502,
        "INVALID_CHEF_ORDER_RESPONSE",
        "The deployed Order Service returned an unsupported chef-order detail shape.",
      );
    }
    return NextResponse.json(order, {
      headers: { "Cache-Control": "no-store" },
    });
  } catch (error) {
    const timedOut = error instanceof Error && error.name === "AbortError";
    if (error instanceof SessionRequiredError) {
      return failure(
        401,
        "AUTHENTICATION_REQUIRED",
        "Sign in again to view this chef order.",
      );
    }
    return failure(
      timedOut ? 504 : 503,
      timedOut ? "CHEF_ORDER_TIMEOUT" : "CHEF_ORDER_UNAVAILABLE",
      timedOut
        ? "Chef order took too long to respond."
        : "Chef order is temporarily unavailable.",
    );
  }
}
