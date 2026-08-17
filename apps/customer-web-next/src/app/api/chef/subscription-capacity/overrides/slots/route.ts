import { NextRequest, NextResponse } from "next/server";
import { parseDateOverrideInput } from "@/lib/chef-subscription-capacity-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

export async function PUT(request: NextRequest) {
  if (!isSameOrigin(request)) return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const input = parseDateOverrideInput(await request.json().catch(() => null));
  if (!input) return NextResponse.json({ code: "INVALID_CAPACITY_OVERRIDE" }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, "/chef/subscription-capacity/overrides/slots", {
      method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input),
    });
    const body = await upstream.json().catch(() => null);
    if (!upstream.ok) return NextResponse.json({ code: upstream.status === 401 ? "SESSION_EXPIRED" : upstream.status === 403 ? "CHEF_ACCESS_REQUIRED" : "CAPACITY_OVERRIDE_FAILED", message: typeof body?.message === "string" ? body.message : "Capacity override could not be saved." }, { status: upstream.status });
    return NextResponse.json(body, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    if (error instanceof SessionRequiredError) return NextResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 });
    return NextResponse.json({ code: "CAPACITY_UNAVAILABLE" }, { status: 503 });
  }
}
