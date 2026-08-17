import { NextRequest, NextResponse } from "next/server";
import { clearSessionCookies, setSessionCookies } from "@/lib/auth-cookies";
import { parseSessionExchange } from "@/lib/auth-contract";
import { isSameOrigin } from "@/lib/request-security";
import { apiBaseUrl } from "@/lib/server-api";

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const refreshToken = request.cookies.get("craves_refresh_token")?.value;
  if (!refreshToken) return NextResponse.json({ code: "REFRESH_REQUIRED" }, { status: 401 });
  try {
    const upstream = await fetch(`${apiBaseUrl()}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ refreshToken }),
      cache: "no-store",
    });
    const session = upstream.ok ? parseSessionExchange(await upstream.json().catch(() => null)) : null;
    if (!session) {
      const response = NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
      clearSessionCookies(response);
      return response;
    }
    const response = NextResponse.json({ identity: session.identity }, { headers: { "Cache-Control": "no-store" } });
    setSessionCookies(response, session);
    return response;
  } catch {
    return NextResponse.json({ code: "REFRESH_UNAVAILABLE" }, { status: 503 });
  }
}
