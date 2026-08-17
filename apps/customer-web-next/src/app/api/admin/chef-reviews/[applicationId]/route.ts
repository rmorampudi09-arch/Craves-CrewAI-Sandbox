import { NextRequest, NextResponse } from "next/server";
import { parseAdminChefApplication } from "@/lib/admin-chef-review-contract";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest, context: { params: Promise<{ applicationId: string }> }) {
  const { applicationId } = await context.params;
  if (!isUuid(applicationId)) return NextResponse.json({ code: "INVALID_APPLICATION_ID" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/backoffice/chef-reviews/${applicationId}`);
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "ADMIN_ACCESS_REQUIRED" : upstream.status === 404 ? "CHEF_APPLICATION_NOT_FOUND" : "CHEF_REVIEW_UNAVAILABLE" }, { status: upstream.status });
    const application = parseAdminChefApplication(body);
    return application ? NextResponse.json(application, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_CHEF_REVIEW_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
    return NextResponse.json({ code: "CHEF_REVIEW_UNAVAILABLE" }, { status: 503 });
  }
}
