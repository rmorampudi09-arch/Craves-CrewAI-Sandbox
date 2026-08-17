import { NextRequest, NextResponse } from "next/server";
import {
  parseNotificationRecoveryRequest,
  parseNotificationRecoveryResult
} from "@/lib/admin-notification-recovery-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
type Context = { params: Promise<{ requestId: string }> };

function failure(status: number, code: string) {
  return NextResponse.json({ code }, { status, headers: { "Cache-Control": "no-store" } });
}

function errorCode(status: number): string {
  if (status === 401) return "SESSION_EXPIRED";
  if (status === 403) return "ADMIN_ACCESS_REQUIRED";
  if (status === 404) return "NOTIFICATION_REQUEST_NOT_FOUND";
  if (status === 409) return "NOTIFICATION_RETRY_CONFLICT";
  if (status === 400) return "INVALID_NOTIFICATION_RECOVERY_REQUEST";
  if (status === 503) return "NOTIFICATION_RECOVERY_DISABLED";
  return "NOTIFICATION_RECOVERY_FAILED";
}

export async function POST(request: NextRequest, context: Context) {
  if (!isSameOrigin(request)) return failure(403, "CROSS_ORIGIN_REQUEST_REJECTED");
  const requestId = (await context.params).requestId.trim().toLowerCase();
  const input = parseNotificationRecoveryRequest(await request.json().catch(() => null));
  if (!UUID.test(requestId) || !input || input.requestId !== requestId) return failure(400, "INVALID_NOTIFICATION_RECOVERY_REQUEST");

  try {
    const upstream = await authenticatedApiFetch(
      request,
      `/admin/notifications/operations/${requestId}/retry`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: input.reason })
      },
      15_000
    );
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return failure(upstream.status, errorCode(upstream.status));
    const result = parseNotificationRecoveryResult(body);
    if (!result || result.requestId !== requestId) return failure(502, "INVALID_NOTIFICATION_RECOVERY_RESPONSE");
    return NextResponse.json(result, {
      headers: { "Cache-Control": "no-store", "X-Correlation-ID": result.correlationId }
    });
  } catch (error) {
    return error instanceof SessionRequiredError
      ? failure(401, "AUTHENTICATION_REQUIRED")
      : failure(503, "NOTIFICATION_RECOVERY_UNAVAILABLE");
  }
}
