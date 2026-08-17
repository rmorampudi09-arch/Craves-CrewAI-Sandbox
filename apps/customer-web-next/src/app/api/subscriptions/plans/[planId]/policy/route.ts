import { NextResponse } from "next/server";
import { parseSubscriptionPlanPolicy } from "@/lib/subscription-lifecycle-contract";
import { apiBaseUrl, isUuid } from "@/lib/server-api";

export const dynamic = "force-dynamic";

export async function GET(_request: Request, context: { params: Promise<{ planId: string }> }) {
  const { planId } = await context.params;
  if (!isUuid(planId)) return NextResponse.json({ code: "INVALID_PLAN_ID" }, { status: 400 });
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10_000);
  try {
    const upstream = await fetch(`${apiBaseUrl()}/subscriptions/plans/${planId}/policy`, {
      headers: { Accept: "application/json" }, cache: "no-store", signal: controller.signal,
    });
    if (!upstream.ok) return NextResponse.json({ code: "SUBSCRIPTION_POLICY_UNAVAILABLE" }, { status: upstream.status });
    const policy = parseSubscriptionPlanPolicy(await upstream.json().catch(() => null));
    return policy ? NextResponse.json(policy, { headers: { "Cache-Control": "no-store" } }) : NextResponse.json({ code: "INVALID_SUBSCRIPTION_POLICY_RESPONSE" }, { status: 502 });
  } catch (error) {
    const timedOut = error instanceof Error && error.name === "AbortError";
    return NextResponse.json({ code: timedOut ? "SUBSCRIPTION_POLICY_TIMEOUT" : "SUBSCRIPTION_POLICY_UNAVAILABLE" }, { status: timedOut ? 504 : 503 });
  } finally {
    clearTimeout(timeout);
  }
}
