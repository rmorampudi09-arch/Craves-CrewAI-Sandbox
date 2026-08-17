import { NextRequest, NextResponse } from "next/server";
import {
  parseAdminAccountAction,
  parseAdminAccountInterventionStatus
} from "@/lib/admin-account-intervention-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

type Context = { params: Promise<{ identityId: string }> };

function failure(status: number, code: string) {
  return NextResponse.json({ code }, { status, headers: { "Cache-Control": "no-store" } });
}

function statusCode(status: number): string {
  if (status === 401) return "SESSION_EXPIRED";
  if (status === 403) return "ADMIN_ACCESS_REQUIRED";
  if (status === 404) return "IDENTITY_NOT_FOUND";
  if (status === 409) return "ACCOUNT_INTERVENTION_CONFLICT";
  if (status === 400) return "INVALID_ACCOUNT_INTERVENTION_REQUEST";
  if (status === 503) return "ACCOUNT_INTERVENTION_DISABLED";
  return "ACCOUNT_INTERVENTION_FAILED";
}

async function identityId(context: Context): Promise<string | null> {
  const value = (await context.params).identityId.trim().toLowerCase();
  return UUID.test(value) ? value : null;
}

export async function GET(request: NextRequest, context: Context) {
  const id = await identityId(context);
  if (!id) return failure(400, "INVALID_IDENTITY_ID");
  try {
    const upstream = await authenticatedApiFetch(request, `/admin/accounts/${id}/intervention-status`, {}, 15_000);
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return failure(upstream.status, statusCode(upstream.status));
    const result = parseAdminAccountInterventionStatus(body);
    if (!result || result.identityId !== id) {
      return failure(502, "INVALID_ACCOUNT_INTERVENTION_RESPONSE");
    }
    return NextResponse.json(result, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    return error instanceof SessionRequiredError
      ? failure(401, "AUTHENTICATION_REQUIRED")
      : failure(503, "ACCOUNT_INTERVENTION_UNAVAILABLE");
  }
}

export async function POST(request: NextRequest, context: Context) {
  if (!isSameOrigin(request)) return failure(403, "CROSS_ORIGIN_REQUEST_REJECTED");
  const id = await identityId(context);
  const input = parseAdminAccountAction(await request.json().catch(() => null));
  if (!id || !input || input.identityId !== id) return failure(400, "INVALID_ACCOUNT_INTERVENTION_REQUEST");

  const endpoint = input.action === "SUSPEND" ? "suspend" : "reactivate";
  try {
    const upstream = await authenticatedApiFetch(request, `/admin/accounts/${id}/${endpoint}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ reason: input.reason })
    }, 15_000);
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return failure(upstream.status, statusCode(upstream.status));
    const result = parseAdminAccountInterventionStatus(body);
    if (!result || result.identityId !== id || result.action !== input.action) {
      return failure(502, "INVALID_ACCOUNT_INTERVENTION_RESPONSE");
    }
    return NextResponse.json(result, {
      headers: {
        "Cache-Control": "no-store",
        ...(result.correlationId ? { "X-Correlation-ID": result.correlationId } : {})
      }
    });
  } catch (error) {
    return error instanceof SessionRequiredError
      ? failure(401, "AUTHENTICATION_REQUIRED")
      : failure(503, "ACCOUNT_INTERVENTION_UNAVAILABLE");
  }
}
