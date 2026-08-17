import { NextRequest, NextResponse } from "next/server";
import { clearSessionCookies } from "@/lib/auth-cookies";
import { isSameOrigin } from "@/lib/request-security";
import { apiBaseUrl } from "@/lib/server-api";

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const refreshToken = request.cookies.get("craves_refresh_token")?.value;
  if (refreshToken) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 5_000);
    try {
      await fetch(`${apiBaseUrl()}/auth/logout`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({ refreshToken }),
        cache: "no-store",
        signal: controller.signal,
      });
    } catch {
      // Local cookies are still cleared. The backend session naturally expires if unavailable.
    } finally {
      clearTimeout(timeout);
    }
  }
  const response = NextResponse.json({ signedOut: true }, { headers: { "Cache-Control": "no-store" } });
  clearSessionCookies(response);
  return response;
}
