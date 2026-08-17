import { NextRequest, NextResponse } from "next/server";
import { parseChefCapacitySummary } from "@/lib/admin-subscription-capacity-contract";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest, context: { params: Promise<{ chefIdentityId: string }> }) {
  const { chefIdentityId } = await context.params;
  if (!isUuid(chefIdentityId)) return NextResponse.json({ code: "INVALID_CHEF_ID" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/admin/subscription-capacity/chefs/${chefIdentityId}`);
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : "CAPACITY_REQUEST_FAILED" }, { status: upstream.status });
    const summary = parseChefCapacitySummary(body);
    return summary ? NextResponse.json(summary, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_CAPACITY_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    return NextResponse.json({ code: "CAPACITY_UNAVAILABLE" }, { status: 503 });
  }
}
