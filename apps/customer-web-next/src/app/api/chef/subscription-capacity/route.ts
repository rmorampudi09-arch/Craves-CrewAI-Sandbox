import { NextRequest, NextResponse } from "next/server";
import { parseChefCapacitySummary } from "@/lib/chef-subscription-capacity-contract";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest) {
  try {
    const upstream = await authenticatedApiFetch(request, "/chef/subscription-capacity");
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) {
      return NextResponse.json(
        {
          code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "CHEF_ACCESS_REQUIRED" : "CAPACITY_REQUEST_FAILED",
          message: upstream.status === 403 ? "An approved chef account is required to manage capacity." : "Subscription capacity is temporarily unavailable.",
        },
        { status: upstream.status },
      );
    }
    const summary = parseChefCapacitySummary(body);
    return summary
      ? NextResponse.json(summary, { headers: { "Cache-Control": "no-store" } })
      : NextResponse.json({ code: "INVALID_CAPACITY_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    const timedOut = error instanceof Error && error.name === "AbortError";
    return NextResponse.json({ code: timedOut ? "CAPACITY_TIMEOUT" : "CAPACITY_UNAVAILABLE" }, { status: timedOut ? 504 : 503 });
  }
}
