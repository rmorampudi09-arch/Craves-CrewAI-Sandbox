import { isSameOrigin } from "@/lib/request-security";
import { NextRequest, NextResponse } from "next/server";
import {
  parseChefApplication,
  parseChefApplicationInput,
} from "@/lib/chef-application-contract";

export const dynamic = "force-dynamic";

function apiBaseUrl(): string {
  const value = process.env.CRAVES_API_BASE_URL?.trim();
  if (!value?.startsWith("https://"))
    throw new Error("CRAVES_API_BASE_URL must use HTTPS");
  return value.replace(/\/$/, "");
}

function upstreamFailure(
  status: number,
  raw: unknown,
): { code: string; message: string } {
  const body =
    raw && typeof raw === "object" ? (raw as Record<string, unknown>) : {};
  const upstreamCode =
    typeof body.code === "string" && /^[A-Z0-9_]{3,80}$/.test(body.code)
      ? body.code
      : null;
  if (status === 401) {
    return {
      code: "SESSION_EXPIRED",
      message: "Your session expired. Sign in again.",
    };
  }
  if (upstreamCode === "CHEF_ALREADY_APPROVED") {
    return {
      code: upstreamCode,
      message:
        "This chef application is already approved and cannot be resubmitted.",
    };
  }
  if (status === 409) {
    return {
      code: upstreamCode ?? "CHEF_APPLICATION_CONFLICT",
      message:
        "The chef application changed in the backend. Reload and try again.",
    };
  }
  if (status === 400) {
    return {
      code: upstreamCode ?? "INVALID_CHEF_APPLICATION",
      message: "Complete all required chef details using valid values.",
    };
  }
  return {
    code: upstreamCode ?? "CHEF_APPLICATION_REQUEST_FAILED",
    message: "Chef application is temporarily unavailable.",
  };
}

async function forward(
  request: NextRequest,
  method: "GET" | "POST",
  body?: unknown,
) {
  const token = request.cookies.get("craves_access_token")?.value;
  if (!token)
    return NextResponse.json(
      { code: "AUTHENTICATION_REQUIRED" },
      { status: 401 },
    );
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12_000);
  try {
    const upstream = await fetch(`${apiBaseUrl()}/chef/application`, {
      method,
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/json",
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store",
      signal: controller.signal,
    });
    if (!upstream.ok) {
      const failure = upstreamFailure(
        upstream.status,
        await upstream.json().catch(() => null),
      );
      const response = NextResponse.json(
        failure,
        { status: upstream.status },
      );
      if (upstream.status === 401)
        response.cookies.delete("craves_access_token");
      return response;
    }
    const application = parseChefApplication(
      await upstream.json().catch(() => null),
    );
    if (!application)
      return NextResponse.json(
        { code: "INVALID_CHEF_APPLICATION_RESPONSE" },
        { status: 502 },
      );
    const response = NextResponse.json(application);
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch (error) {
    const timedOut = error instanceof Error && error.name === "AbortError";
    return NextResponse.json(
      {
        code: timedOut
          ? "CHEF_APPLICATION_TIMEOUT"
          : "CHEF_APPLICATION_UNAVAILABLE",
      },
      { status: timedOut ? 504 : 503 },
    );
  } finally {
    clearTimeout(timeout);
  }
}

export async function GET(request: NextRequest) {
  return forward(request, "GET");
}

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request))
    return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  const input = parseChefApplicationInput(
    await request.json().catch(() => null),
  );
  if (!input)
    return NextResponse.json(
      { code: "INVALID_CHEF_APPLICATION" },
      { status: 400 },
    );
  return forward(request, "POST", input);
}
