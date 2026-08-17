import { NextRequest, NextResponse } from "next/server";
import { parseSubscriptionOccurrences } from "@/lib/subscription-lifecycle-contract";
import { authenticatedApiFetch, isUuid, SessionRequiredError } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export async function GET(request: NextRequest, context: { params: Promise<{ subscriptionId: string }> }) {
  const { subscriptionId } = await context.params;
  if (!isUuid(subscriptionId)) return NextResponse.json({ code: "INVALID_SUBSCRIPTION_ID" }, { status: 400 });
  const requested = Number(request.nextUrl.searchParams.get("limit") ?? "100");
  const limit = Number.isInteger(requested) ? Math.min(200, Math.max(1, requested)) : 100;
  try {
    const upstream = await authenticatedApiFetch(request, `/subscriptions/${subscriptionId}/occurrences?limit=${limit}`);
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : "SUBSCRIPTION_OCCURRENCES_FAILED" }, { status: upstream.status });
    const occurrences = parseSubscriptionOccurrences(body);
    return occurrences ? NextResponse.json(occurrences, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_SUBSCRIPTION_OCCURRENCES_RESPONSE" }, { status: 502 });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    return NextResponse.json({ code: "SUBSCRIPTION_UNAVAILABLE" }, { status: 503 });
  }
}
