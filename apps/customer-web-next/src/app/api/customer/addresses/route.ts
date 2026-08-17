import { NextRequest, NextResponse } from "next/server";
import {
  parseAddressInput,
  parseCustomerAddress,
  parseCustomerAddresses,
} from "@/lib/address-contract";
import { isSameOrigin } from "@/lib/request-security";
import {
  authenticatedApiFetch,
  SessionRequiredError,
} from "@/lib/server-api";

function record(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function safeUpstreamMessage(body: unknown): string | null {
  const raw = record(body);
  const message = raw?.message;
  if (typeof message !== "string") return null;
  const trimmed = message.trim();
  if (!trimmed || trimmed.length > 240) return null;
  return /\b(latitude|longitude|coordinates?)\b/i.test(trimmed) ? null : trimmed;
}

function failure(status: number, body: unknown = null, correlationId: string | null = null) {
  const message = status === 401
    ? "Please sign in again."
    : status === 400
      ? safeUpstreamMessage(body) ?? "Confirm the complete delivery address and current location."
      : status === 404
        ? "Address was not found."
        : status === 502
          ? "Craves received an invalid saved-address response."
          : status >= 500
            ? "The address service is temporarily unavailable."
            : safeUpstreamMessage(body) ?? "Address request could not be completed.";

  return NextResponse.json({
    error: status === 401
      ? "SESSION_REQUIRED"
      : status === 404
        ? "ADDRESS_NOT_FOUND"
        : status === 502
          ? "INVALID_ADDRESS_RESPONSE"
          : "ADDRESS_REQUEST_FAILED",
    message,
    upstreamStatus: status,
    ...(correlationId ? { correlationId } : {}),
  }, { status });
}

function correlationId(response: Response): string | null {
  return response.headers.get("x-correlation-id")
    ?? response.headers.get("x-request-id")
    ?? null;
}

export async function GET(request: NextRequest) {
  try {
    const upstream = await authenticatedApiFetch(request, "/customer/addresses");
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return failure(upstream.status, body, correlationId(upstream));

    const addresses = parseCustomerAddresses(body);
    return addresses
      ? NextResponse.json(addresses, { headers: { "Cache-Control": "no-store" } })
      : failure(502, body, correlationId(upstream));
  } catch (error) {
    return error instanceof SessionRequiredError
      ? failure(401)
      : failure(503);
  }
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) {
    return NextResponse.json({
      error: "ORIGIN_REJECTED",
      message: "Invalid address request origin.",
    }, { status: 403 });
  }

  const input = parseAddressInput(await request.json().catch(() => null));
  if (!input) return failure(400);

  try {
    const upstream = await authenticatedApiFetch(request, "/customer/addresses", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input),
    });
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return failure(upstream.status, body, correlationId(upstream));

    const address = parseCustomerAddress(body);
    return address
      ? NextResponse.json(address, {
          status: 201,
          headers: { "Cache-Control": "no-store" },
        })
      : failure(502, body, correlationId(upstream));
  } catch (error) {
    return error instanceof SessionRequiredError
      ? failure(401)
      : failure(503);
  }
}
