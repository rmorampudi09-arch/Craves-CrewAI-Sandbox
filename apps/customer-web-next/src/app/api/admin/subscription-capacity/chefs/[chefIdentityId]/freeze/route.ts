import { NextRequest, NextResponse } from "next/server";
import { parseChefCapacitySummary } from "@/lib/admin-subscription-capacity-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export async function PATCH(request: NextRequest, context: { params: Promise<{ chefIdentityId: string }> }) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const { chefIdentityId } = await context.params;
  if (!isUuid(chefIdentityId)) return NextResponse.json({ code: "INVALID_CHEF_ID" }, { status: 400 });
  const raw = await request.json().catch(() => null) as Record<string, unknown> | null;
  const frozen = raw?.frozen;
  const reason = typeof raw?.reason === "string" && raw.reason.trim().length > 0 && raw.reason.trim().length <= 1000 ? raw.reason.trim() : null;
  if (typeof frozen !== "boolean" || !reason) return NextResponse.json({ code: "INVALID_FREEZE_INPUT" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/admin/subscription-capacity/chefs/${chefIdentityId}/freeze`, {
      method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ frozen, reason }),
    });
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : "CAPACITY_FREEZE_FAILED" }, { status: upstream.status });
    const summary = parseChefCapacitySummary(body);
    return summary ? NextResponse.json(summary, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_CAPACITY_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    return NextResponse.json({ code: "CAPACITY_UNAVAILABLE" }, { status: 503 });
  }
}
