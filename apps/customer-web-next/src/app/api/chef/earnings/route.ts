import { NextRequest, NextResponse } from "next/server";
import { parseChefEarnings } from "@/lib/chef-earnings-contract";
import {
  authenticatedApiFetch,
  SessionRequiredError,
} from "@/lib/server-api";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  try {
    const upstream = await authenticatedApiFetch(request, "/chef/earnings?limit=200");
    const raw = await upstream.json().catch(() => null);
    if (!upstream.ok) {
      return NextResponse.json(
        {
          code:
            upstream.status === 401
              ? "SESSION_EXPIRED"
              : upstream.status === 403
                ? "CHEF_ACCESS_REQUIRED"
                : "CHEF_EARNINGS_REQUEST_FAILED",
          message:
            upstream.status === 403
              ? "An approved chef role is required to view this ledger."
              : "Chef earnings are temporarily unavailable.",
        },
        { status: upstream.status },
      );
    }
    const entries = parseChefEarnings(raw);
    return entries
      ? NextResponse.json(entries, {
          headers: { "Cache-Control": "no-store" },
        })
      : NextResponse.json(
          {
            code: "INVALID_CHEF_EARNINGS_RESPONSE",
            message: "Chef earnings response validation failed.",
          },
          { status: 502 },
        );
  } catch (error) {
    const timeout = error instanceof Error && error.name === "AbortError";
    return NextResponse.json(
      {
        code:
          error instanceof SessionRequiredError
            ? "AUTHENTICATION_REQUIRED"
            : timeout
              ? "CHEF_EARNINGS_TIMEOUT"
              : "CHEF_EARNINGS_UNAVAILABLE",
        message:
          error instanceof SessionRequiredError
            ? "Sign in again to view chef earnings."
            : timeout
              ? "Chef earnings took too long to respond."
              : "Chef earnings are temporarily unavailable.",
      },
      {
        status: error instanceof SessionRequiredError ? 401 : timeout ? 504 : 503,
      },
    );
  }
}
