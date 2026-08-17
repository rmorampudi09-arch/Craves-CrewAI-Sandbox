import { NextRequest, NextResponse } from "next/server";
import { parseAdminChefApplications } from "@/lib/admin-chef-review-contract";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";
const ALLOWED_STATUSES = new Set(["PENDING", "APPROVED", "REJECTED"]);

export async function GET(request: NextRequest) {
  const status = request.nextUrl.searchParams.get("status")?.toUpperCase() ?? "PENDING";
  if (!ALLOWED_STATUSES.has(status)) return NextResponse.json({ code: "INVALID_REVIEW_STATUS" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/backoffice/chef-reviews?status=${encodeURIComponent(status)}`);
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : "CHEF_REVIEWS_UNAVAILABLE" }, { status: upstream.status });
    const applications = parseAdminChefApplications(body);
    return applications ? NextResponse.json(applications, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_CHEF_REVIEW_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
    return NextResponse.json({ code: "CHEF_REVIEWS_UNAVAILABLE" }, { status: 503 });
  }
}
