import { NextResponse } from "next/server";
import { parsePublicSubscriptionPlans } from "@/lib/subscription-contract";

export const dynamic = "force-dynamic";

const SUBSCRIPTION_UPSTREAM_TIMEOUT_MS = 30_000;

function apiBaseUrl(): string {
  const value = process.env.CRAVES_API_BASE_URL?.trim();
  if (!value?.startsWith("https://")) throw new Error("CRAVES_API_BASE_URL must use HTTPS");
  return value.replace(/\/$/, "");
}

export async function GET() {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), SUBSCRIPTION_UPSTREAM_TIMEOUT_MS);
  try {
    const upstream = await fetch(`${apiBaseUrl()}/subscriptions/plans`, {
      headers: { Accept: "application/json" },
      cache: "no-store",
      signal: controller.signal
    });
    if (!upstream.ok) return NextResponse.json({ code: "SUBSCRIPTION_PLANS_UNAVAILABLE" }, { status: upstream.status });
    const plans = parsePublicSubscriptionPlans(await upstream.json().catch(() => null));
    if (!plans) return NextResponse.json({ code: "INVALID_SUBSCRIPTION_PLANS_RESPONSE" }, { status: 502 });
    const response = NextResponse.json(plans);
    response.headers.set("Cache-Control", "no-store, no-cache, must-revalidate");
    return response;
  } catch (error) {
    const timedOut = error instanceof Error && error.name === "AbortError";
    return NextResponse.json({ code: timedOut ? "SUBSCRIPTION_PLANS_TIMEOUT" : "SUBSCRIPTION_PLANS_UNAVAILABLE" }, { status: timedOut ? 504 : 503 });
  } finally {
    clearTimeout(timeout);
  }
}
