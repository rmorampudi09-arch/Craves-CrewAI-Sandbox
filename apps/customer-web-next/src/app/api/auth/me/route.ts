import { NextRequest, NextResponse } from "next/server";
import { clearSessionCookies } from "@/lib/auth-cookies";
import { parseIdentity } from "@/lib/auth-contract";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export async function GET(request: NextRequest) {
  try {
    const upstream = await authenticatedApiFetch(request, "/auth/me", {}, 8_000);
    const raw = await upstream.json().catch(() => null) as { identity?: unknown } | null;
    if (!upstream.ok) {
      const response = NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : "IDENTITY_UNAVAILABLE" }, { status: upstream.status });
      if (upstream.status === 401) clearSessionCookies(response);
      return response;
    }
    const identity = parseIdentity(raw?.identity);
    if (!identity) return NextResponse.json({ code: "INVALID_IDENTITY_RESPONSE" }, { status: 502 });
    return NextResponse.json(identity, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
    return NextResponse.json({ code: "IDENTITY_UNAVAILABLE" }, { status: 503 });
  }
}
