import { NextRequest, NextResponse } from "next/server";
import { parseAdminDashboardSummary } from "@/lib/admin-dashboard-contract";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  try {
    const upstream = await authenticatedApiFetch(request, "/admin/dashboard/summary", {}, 10_000);
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) {
      const code = upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : "DASHBOARD_UNAVAILABLE";
      return NextResponse.json({ code }, { status: upstream.status, headers: { "Cache-Control": "no-store" } });
    }
    const summary = parseAdminDashboardSummary(body);
    return summary
      ? NextResponse.json(summary, { headers: { "Cache-Control": "no-store" } })
      : NextResponse.json({ code: "INVALID_DASHBOARD_RESPONSE" }, { status: 502, headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return NextResponse.json(
      { code: error instanceof SessionRequiredError ? "AUTHENTICATION_REQUIRED" : "DASHBOARD_UNAVAILABLE" },
      { status: error instanceof SessionRequiredError ? 401 : 503, headers: { "Cache-Control": "no-store" } }
    );
  }
}
