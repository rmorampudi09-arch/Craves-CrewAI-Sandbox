import { NextRequest, NextResponse } from "next/server";
import { parseAdminIdentity } from "@/lib/admin-contract";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  try {
    const upstream = await authenticatedApiFetch(request, "/auth/me");
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : "IDENTITY_UNAVAILABLE" }, { status: upstream.status });
    const identity = parseAdminIdentity(body);
    if (!identity) return NextResponse.json({ code: "INVALID_IDENTITY_RESPONSE" }, { status: 502 });
    if (!identity.adminEnabled) return NextResponse.json({ code: "ADMIN_ACCESS_REQUIRED" }, { status: 403 });
    return NextResponse.json(identity, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
    return NextResponse.json({ code: "IDENTITY_UNAVAILABLE" }, { status: 503 });
  }
}
