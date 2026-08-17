import { isSameOrigin } from "@/lib/request-security";
import { NextRequest, NextResponse } from "next/server";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";


export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ noticeId: string }> }
) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const { noticeId } = await params;
  if (!isUuid(noticeId)) return NextResponse.json({ code: "INVALID_NOTICE_ID" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, `/notifications/in-app/${noticeId}/read`, { method: "PATCH" });
    if (!upstream.ok) {
      const code = upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 404 ? "NOTICE_NOT_FOUND" : "NOTICE_UPDATE_FAILED";
      return NextResponse.json({ code }, { status: upstream.status });
    }
    return new NextResponse(null, { status: 204, headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
    return NextResponse.json({ code: "NOTICE_UPDATE_FAILED" }, { status: 503 });
  }
}
