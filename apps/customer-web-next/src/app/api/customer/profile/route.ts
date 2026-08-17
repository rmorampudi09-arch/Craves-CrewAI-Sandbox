import { NextRequest, NextResponse } from "next/server";
import { parseCustomerProfile, parseProfileInput } from "@/lib/profile-contract";
import { isSameOrigin } from "@/lib/request-security";
import { authenticatedApiFetch, SessionRequiredError } from "@/lib/server-api";

function failure(status: number, message = "Customer profile is unavailable.") {
  return NextResponse.json({ error: status === 401 ? "SESSION_REQUIRED" : "PROFILE_REQUEST_FAILED", message: status === 401 ? "Please sign in again." : message }, { status });
}

export async function GET(request: NextRequest) {
  try {
    const upstream = await authenticatedApiFetch(request, "/customer/profile");
    const raw = await upstream.json().catch(() => null);
    if (!upstream.ok) return failure(upstream.status);
    const profile = parseCustomerProfile(raw);
    return profile ? NextResponse.json(profile, { headers: { "Cache-Control": "no-store" } }) : failure(502, "Customer profile response validation failed.");
  } catch (error) {
    if (error instanceof SessionRequiredError) return failure(401);
    return failure(503);
  }
}

export async function PUT(request: NextRequest) {
  if (!isSameOrigin(request)) return NextResponse.json({ error: "ORIGIN_REJECTED", message: "Invalid profile request origin." }, { status: 403 });
  const input = parseProfileInput(await request.json().catch(() => null));
  if (!input) return NextResponse.json({ error: "INVALID_PROFILE", message: "Enter a valid first name, last name and optional email." }, { status: 400 });
  try {
    const upstream = await authenticatedApiFetch(request, "/customer/profile", { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(input) });
    const raw = await upstream.json().catch(() => null);
    if (!upstream.ok) return failure(upstream.status, "Customer profile could not be saved.");
    const profile = parseCustomerProfile(raw);
    return profile ? NextResponse.json(profile, { headers: { "Cache-Control": "no-store" } }) : failure(502, "Customer profile response validation failed.");
  } catch (error) {
    if (error instanceof SessionRequiredError) return failure(401);
    return failure(503, "Customer profile could not be saved.");
  }
}
