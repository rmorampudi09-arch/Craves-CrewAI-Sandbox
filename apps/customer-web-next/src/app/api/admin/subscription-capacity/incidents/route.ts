import { NextRequest, NextResponse } from "next/server";
import { parseCapacityIncidentPage } from "@/lib/admin-subscription-capacity-contract";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  const chefIdentityId = request.nextUrl.searchParams.get("chefIdentityId")?.trim() ?? "";
  const status = request.nextUrl.searchParams.get("status")?.trim().toUpperCase() ?? "OPEN";
  const afterCreatedAt = request.nextUrl.searchParams.get("afterCreatedAt")?.trim() ?? "";
  const afterId = request.nextUrl.searchParams.get("afterId")?.trim() ?? "";
  const requested = Number(request.nextUrl.searchParams.get("limit") ?? "50");
  const limit = Number.isInteger(requested) ? Math.min(200, Math.max(1, requested)) : 50;
  if (chefIdentityId && !isUuid(chefIdentityId)) return NextResponse.json({ code: "INVALID_CHEF_ID" }, { status: 400 });
  if (!new Set(["OPEN", "RESOLVED"]).has(status)) return NextResponse.json({ code: "INVALID_INCIDENT_STATUS" }, { status: 400 });
  if ((afterCreatedAt && !afterId) || (!afterCreatedAt && afterId) || (afterId && !isUuid(afterId)) || (afterCreatedAt && Number.isNaN(Date.parse(afterCreatedAt)))) return NextResponse.json({ code: "INVALID_CURSOR" }, { status: 400 });
  const query = new URLSearchParams({ status, limit: String(limit) });
  if (chefIdentityId) query.set("chefIdentityId", chefIdentityId);
  if (afterCreatedAt) query.set("afterCreatedAt", afterCreatedAt);
  if (afterId) query.set("afterId", afterId);
  try {
    const upstream = await authenticatedApiFetch(request, `/admin/subscription-capacity/incidents?${query.toString()}`);
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : "CAPACITY_INCIDENTS_FAILED" }, { status: upstream.status });
    const page = parseCapacityIncidentPage(body);
    return page ? NextResponse.json(page, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_CAPACITY_INCIDENTS_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    return NextResponse.json({ code: "CAPACITY_UNAVAILABLE" }, { status: 503 });
  }
}
