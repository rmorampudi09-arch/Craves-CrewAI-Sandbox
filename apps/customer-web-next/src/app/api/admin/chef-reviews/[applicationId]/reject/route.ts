import { NextRequest, NextResponse } from "next/server";
import { parseAdminChefApplication, parseAdminDecision } from "@/lib/admin-chef-review-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export async function POST(request: NextRequest, context: { params: Promise<{ applicationId: string }> }) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const { applicationId } = await context.params;
  if (!isUuid(applicationId)) return NextResponse.json({ code: "INVALID_APPLICATION_ID" }, { status: 400 });
  const decision = parseAdminDecision(await request.json().catch(() => null));
  if (!decision) return NextResponse.json({ code: "REJECTION_REASON_REQUIRED" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/backoffice/chef-reviews/${applicationId}/reject`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(decision) });
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : "CHEF_REJECTION_FAILED" }, { status: upstream.status });
    const application = parseAdminChefApplication(body);
    return application ? NextResponse.json(application, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_CHEF_REVIEW_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
    return NextResponse.json({ code: "CHEF_REJECTION_UNAVAILABLE" }, { status: 503 });
  }
}
