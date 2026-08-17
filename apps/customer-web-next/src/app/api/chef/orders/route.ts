import { NextRequest, NextResponse } from "next/server";
import { parseChefOrdersResponse } from "@/lib/chef-order-contract";
import {
  authenticatedApiFetch,
  SessionRequiredError,
} from "@/lib/server-api";

export const dynamic = "force-dynamic";

function failure(status: number, code: string, message: string) {
  return NextResponse.json({ code, message }, { status });
}

export async function GET(request: NextRequest) {
  try {
    const upstream = await authenticatedApiFetch(request, "/chef/orders");
    const raw = await upstream.json().catch(() => null);
    if (!upstream.ok) {
      return failure(
        upstream.status,
        upstream.status === 401
          ? "SESSION_EXPIRED"
          : upstream.status === 403
            ? "CHEF_ACCESS_REQUIRED"
            : "CHEF_ORDERS_REQUEST_FAILED",
        upstream.status === 403
          ? "An approved chef role is required to view kitchen orders."
          : "Chef orders are temporarily unavailable.",
      );
    }
    const orders = parseChefOrdersResponse(raw);
    return orders
      ? NextResponse.json(orders, {
          headers: { "Cache-Control": "no-store" },
        })
      : failure(
          502,
          "INVALID_CHEF_ORDERS_RESPONSE",
          "The deployed Order Service returned an unsupported chef-order envelope or record shape.",
        );
  } catch (error) {
    const timedOut = error instanceof Error && error.name === "AbortError";
    if (error instanceof SessionRequiredError) {
      return failure(
        401,
        "AUTHENTICATION_REQUIRED",
        "Sign in again to view chef orders.",
      );
    }
    return failure(
      timedOut ? 504 : 503,
      timedOut ? "CHEF_ORDERS_TIMEOUT" : "CHEF_ORDERS_UNAVAILABLE",
      timedOut
        ? "Chef orders took too long to respond."
        : "Chef orders are temporarily unavailable.",
    );
  }
}
