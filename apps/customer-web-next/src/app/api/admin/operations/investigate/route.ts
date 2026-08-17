import { NextRequest, NextResponse } from "next/server";
import {
  parseAdminInvestigationRequest,
  parseAdminInvestigationResult
} from "@/lib/admin-investigation-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

const PATHS = {
  order: "/admin/operations/orders",
  payment: "/admin/operations/payments",
  refund: "/admin/operations/refunds",
  "delivery-command": "/admin/operations/delivery-commands"
} as const;

function errorCode(status: number): string {
  if (status === 401) return "SESSION_EXPIRED";
  if (status === 403) return "ADMIN_ACCESS_REQUIRED";
  if (status === 404) return "RESOURCE_NOT_FOUND";
  if (status === 400) return "INVALID_INVESTIGATION_REQUEST";
  return "INVESTIGATION_FAILED";
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) {
    return NextResponse.json({ code: "CROSS_ORIGIN_REQUEST_REJECTED" }, { status: 403 });
  }

  const input = parseAdminInvestigationRequest(await request.json().catch(() => null));
  if (!input) {
    return NextResponse.json({ code: "INVALID_INVESTIGATION_REQUEST" }, { status: 400 });
  }

  try {
    const upstream = await authenticatedApiFetch(
      request,
      `${PATHS[input.resource]}/${input.resourceId}`,
      { headers: { "X-Admin-Reason": input.reason } },
      15_000
    );
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) {
      return NextResponse.json({ code: errorCode(upstream.status) }, {
        status: upstream.status,
        headers: { "Cache-Control": "no-store" }
      });
    }

    const correlationId = upstream.headers.get("X-Correlation-ID")?.trim() ?? "";
    const result = parseAdminInvestigationResult(input.resource, body, correlationId);
    if (!result) {
      return NextResponse.json({ code: "INVALID_INVESTIGATION_RESPONSE" }, {
        status: 502,
        headers: { "Cache-Control": "no-store" }
      });
    }

    return NextResponse.json(result, {
      headers: {
        "Cache-Control": "no-store",
        "X-Correlation-ID": result.correlationId
      }
    });
  } catch (error) {
    if (error instanceof SessionRequiredError) {
      return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
    }
    return NextResponse.json({ code: "INVESTIGATION_UNAVAILABLE" }, {
      status: 503,
      headers: { "Cache-Control": "no-store" }
    });
  }
}
