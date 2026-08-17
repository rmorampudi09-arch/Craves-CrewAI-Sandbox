import { NextRequest, NextResponse } from "next/server";
import { parseChefModeIdentity } from "@/lib/chef-mode-contract";

export const dynamic = "force-dynamic";

function apiBaseUrl(): string {
  const value = process.env.CRAVES_API_BASE_URL?.trim();
  if (!value?.startsWith("https://"))
    throw new Error("CRAVES_API_BASE_URL must use HTTPS");
  return value.replace(/\/$/, "");
}

export async function GET(request: NextRequest) {
  const token = request.cookies.get("craves_access_token")?.value;
  if (!token)
    return NextResponse.json(
      { code: "AUTHENTICATION_REQUIRED" },
      { status: 401 },
    );

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 8_000);
  try {
    const upstream = await fetch(`${apiBaseUrl()}/auth/me`, {
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/json",
      },
      cache: "no-store",
      signal: controller.signal,
    });
    if (!upstream.ok) {
      const response = NextResponse.json(
        {
          code:
            upstream.status === 401
              ? "SESSION_EXPIRED"
              : "IDENTITY_UNAVAILABLE",
        },
        { status: upstream.status },
      );
      if (upstream.status === 401)
        response.cookies.delete("craves_access_token");
      return response;
    }

    // Spring Auth Service returns MeResponse as { identity: {...} }.
    // Parse the nested identity rather than rejecting a valid session wrapper.
    const raw = (await upstream.json().catch(() => null)) as {
      identity?: unknown;
    } | null;
    const identity = parseChefModeIdentity(raw?.identity);
    if (!identity)
      return NextResponse.json(
        { code: "INVALID_IDENTITY_RESPONSE" },
        { status: 502 },
      );

    const response = NextResponse.json(identity);
    response.headers.set(
      "Cache-Control",
      "no-store, no-cache, must-revalidate",
    );
    return response;
  } catch (error) {
    const timedOut = error instanceof Error && error.name === "AbortError";
    return NextResponse.json(
      {
        code: timedOut ? "IDENTITY_TIMEOUT" : "IDENTITY_UNAVAILABLE",
      },
      { status: timedOut ? 504 : 503 },
    );
  } finally {
    clearTimeout(timeout);
  }
}
