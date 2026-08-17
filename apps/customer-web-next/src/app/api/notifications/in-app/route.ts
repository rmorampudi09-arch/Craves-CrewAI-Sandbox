import { NextRequest, NextResponse } from "next/server";
import { parseCustomerNotifications } from "@/lib/notification-contract";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";
export async function GET(request: NextRequest) {
  const limitRaw = request.nextUrl.searchParams.get("limit") ?? "50"; const limit = Number(limitRaw);
  if (!Number.isInteger(limit) || limit < 1 || limit > 50) return NextResponse.json({ error: "INVALID_LIMIT", message: "Notification limit must be between 1 and 50." }, { status: 400 });
  try { const upstream = await authenticatedApiFetch(request, `/notifications/in-app?limit=${limit}`); const body = await upstream.json().catch(() => null); if (!upstream.ok) return NextResponse.json({ error: upstream.status === 401 ? "SESSION_REQUIRED" : "NOTIFICATIONS_UNAVAILABLE", message: upstream.status === 401 ? "Please sign in again." : "Notifications could not be loaded." }, { status: upstream.status }); const notices = parseCustomerNotifications(body); return notices ? NextResponse.json(notices, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ error: "INVALID_UPSTREAM_RESPONSE", message: "Notification response validation failed." }, { status: 502 }); }
  catch (error) { return NextResponse.json({ error: error instanceof SessionRequiredError ? "SESSION_REQUIRED" : "NOTIFICATIONS_UNAVAILABLE", message: error instanceof SessionRequiredError ? "Please sign in again." : "Notifications could not be loaded." }, { status: error instanceof SessionRequiredError ? 401 : 503 }); }
}
