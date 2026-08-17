import { NextRequest, NextResponse } from "next/server";
import { parseCustomerNotifications } from "@/lib/notification-contract";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  const requested = Number(request.nextUrl.searchParams.get("limit") ?? "50");
  const limit = Number.isInteger(requested) ? Math.min(Math.max(requested, 1), 50) : 50;
  try {
    const upstream = await authenticatedApiFetch(request, `/notifications/in-app?limit=${limit}`);
    if (!upstream.ok) {
      return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : "NOTIFICATIONS_UNAVAILABLE" }, { status: upstream.status });
    }
    const notices = parseCustomerNotifications(await upstream.json().catch(() => null));
    if (!notices) return NextResponse.json({ code: "INVALID_NOTIFICATIONS_RESPONSE" }, { status: 502 });
    const response = NextResponse.json(notices);
    response.headers.set("Cache-Control", "no-store, no-cache, must-revalidate");
    return response;
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
    const timedOut = error instanceof Error && error.name === "AbortError";
    return NextResponse.json({ code: timedOut ? "NOTIFICATIONS_TIMEOUT" : "NOTIFICATIONS_UNAVAILABLE" }, { status: timedOut ? 504 : 503 });
  }
}
