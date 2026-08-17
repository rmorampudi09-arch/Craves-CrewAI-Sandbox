import { NextRequest, NextResponse } from "next/server";
import {
  parseNotificationBacklog,
  parseNotificationBacklogQuery
} from "@/lib/admin-notification-recovery-contract";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

function failure(status: number, code: string) {
  return NextResponse.json({ code }, { status, headers: { "Cache-Control": "no-store" } });
}

function errorCode(status: number): string {
  if (status === 401) return "SESSION_EXPIRED";
  if (status === 403) return "ADMIN_ACCESS_REQUIRED";
  if (status === 400) return "INVALID_NOTIFICATION_BACKLOG_REQUEST";
  if (status === 503) return "NOTIFICATION_RECOVERY_DISABLED";
  return "NOTIFICATION_BACKLOG_FAILED";
}

export async function GET(request: NextRequest) {
  const query = parseNotificationBacklogQuery(request.nextUrl.searchParams);
  if (!query) return failure(400, "INVALID_NOTIFICATION_BACKLOG_REQUEST");
  try {
    const upstream = await authenticatedApiFetch(
      request,
      `/admin/notifications/operations/backlog?status=${encodeURIComponent(query.status)}&limit=${query.limit}`,
      {},
      15_000
    );
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return failure(upstream.status, errorCode(upstream.status));
    const result = parseNotificationBacklog(body);
    return result ? NextResponse.json(result, { headers: { "Cache-Control": "no-store" } })
      : failure(502, "INVALID_NOTIFICATION_BACKLOG_RESPONSE");
  } catch (error) {
    return error instanceof SessionRequiredError
      ? failure(401, "AUTHENTICATION_REQUIRED")
      : failure(503, "NOTIFICATION_RECOVERY_UNAVAILABLE");
  }
}
