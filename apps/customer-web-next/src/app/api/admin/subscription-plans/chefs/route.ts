import { NextRequest, NextResponse } from "next/server";
import { parseApprovedChefReferences } from "@/lib/admin-subscription-plan-contract";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  try {
    const upstream = await authenticatedApiFetch(request, "/backoffice/chef-reviews?status=APPROVED");
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : "APPROVED_CHEFS_UNAVAILABLE" }, { status: upstream.status });
    const chefs = parseApprovedChefReferences(body);
    return chefs ? NextResponse.json(chefs, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_APPROVED_CHEF_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
    return NextResponse.json({ code: "APPROVED_CHEFS_UNAVAILABLE" }, { status: 503 });
  }
}
