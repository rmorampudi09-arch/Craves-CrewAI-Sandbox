import { NextRequest, NextResponse } from "next/server";
import {
  isCanonicalUuid,
  parseChefOrderResponse,
} from "@/lib/chef-order-contract";
import { isSameOrigin } from "@/lib/request-security";

export const dynamic = "force-dynamic";

function apiBaseUrl(): string {
  const value = process.env.CRAVES_API_BASE_URL?.trim();
  if (!value?.startsWith("https://")) {
    throw new Error("CRAVES_API_BASE_URL must use HTTPS");
  }
  return value.replace(/\/$/, "");
}

export async function POST(
  request: NextRequest,
  context: { params: Promise<{ orderId: string }> },
) {
  if (!isSameOrigin(request)) {
    return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  }
  const { orderId } = await context.params;
  if (!isCanonicalUuid(orderId)) {
    return NextResponse.json({ code: "INVALID_ORDER_ID" }, { status: 400 });
  }
  const raw = (await request.json().catch(() => null)) as {
    reason?: unknown;
    actionId?: unknown;
  } | null;
  const reason =
    typeof raw?.reason === "string" && raw.reason.trim()
      ? raw.reason.trim().slice(0, 500)
      : null;
  const actionId = typeof raw?.actionId === "string" ? raw.actionId : "";
  if (!reason || reason.length < 3 || !isCanonicalUuid(actionId)) {
    return NextResponse.json(
      { code: "INVALID_REJECTION_REQUEST" },
      { status: 400 },
    );
  }
  const token = request.cookies.get("craves_access_token")?.value;
  if (!token) {
    return NextResponse.json(
      { code: "AUTHENTICATION_REQUIRED" },
      { status: 401 },
    );
  }
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12_000);
  try {
    const upstream = await fetch(
      `${apiBaseUrl()}/chef/orders/${encodeURIComponent(orderId)}/reject`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: "application/json",
          "Content-Type": "application/json",
          "X-Correlation-ID": actionId,
          "Idempotency-Key": actionId,
        },
        body: JSON.stringify({ reason }),
        cache: "no-store",
        signal: controller.signal,
      },
    );
    if (!upstream.ok) {
      const response = NextResponse.json(
        {
          code:
            upstream.status === 401
              ? "SESSION_EXPIRED"
              : upstream.status === 409
                ? "CHEF_REJECTION_CONFLICT"
                : "CHEF_REJECTION_FAILED",
          message:
            upstream.status === 409
              ? "The order state changed or the rejection window is no longer valid."
              : "The order could not be rejected.",
        },
        { status: upstream.status },
      );
      if (upstream.status === 401) {
        response.cookies.delete("craves_access_token");
      }
      return response;
    }
    const order = parseChefOrderResponse(await upstream.json().catch(() => null));
    if (!order || order.id.toLowerCase() !== orderId.toLowerCase()) {
      return NextResponse.json(
        {
          code: "INVALID_CHEF_ORDER_RESPONSE",
          message: "Order Service returned an invalid rejected-order response.",
        },
        { status: 502 },
      );
    }
    const response = NextResponse.json(order);
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch (error) {
    const timedOut = error instanceof Error && error.name === "AbortError";
    return NextResponse.json(
      {
        code: timedOut
          ? "CHEF_REJECTION_TIMEOUT"
          : "CHEF_REJECTION_UNAVAILABLE",
        message: timedOut
          ? "Order rejection took too long to respond."
          : "Order rejection is temporarily unavailable.",
      },
      { status: timedOut ? 504 : 503 },
    );
  } finally {
    clearTimeout(timeout);
  }
}
