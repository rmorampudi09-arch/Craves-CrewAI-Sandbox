import { isSameOrigin } from "@/lib/request-security";
import { NextRequest, NextResponse } from "next/server";
import {
  parseChefKitchen,
  parseChefKitchenInput,
} from "@/lib/chef-kitchen-contract";

export const dynamic = "force-dynamic";

function apiBaseUrl(): string {
  const value = process.env.CRAVES_API_BASE_URL?.trim();
  if (!value?.startsWith("https://")) {
    throw new Error("CRAVES_API_BASE_URL must use HTTPS");
  }
  return value.replace(/\/$/, "");
}

async function call(
  request: NextRequest,
  method: "GET" | "PUT",
  body?: unknown,
) {
  const token = request.cookies.get("craves_access_token")?.value;
  if (!token) {
    return NextResponse.json({ code: "AUTHENTICATION_REQUIRED" }, { status: 401 });
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10_000);
  try {
    const upstream = await fetch(`${apiBaseUrl()}/kitchens/me`, {
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

    if (method === "GET" && upstream.status === 404) {
      return NextResponse.json(null, {
        status: 200,
        headers: { "Cache-Control": "no-store" },
      });
    }

    if (!upstream.ok) {
      const response = NextResponse.json(
        {
          code:
            upstream.status === 401
              ? "SESSION_EXPIRED"
              : upstream.status === 403
                ? "CHEF_ACCESS_REQUIRED"
                : "KITCHEN_REQUEST_FAILED",
          message:
            upstream.status === 401
              ? "Your session expired. Sign in again."
              : upstream.status === 403
                ? "An approved CHEF role is required. Sign out and sign in again after approval."
                : upstream.status === 400
                  ? "Complete the required kitchen fields using valid values."
                  : "Kitchen profile is temporarily unavailable.",
        },
        { status: upstream.status },
      );

      if (upstream.status === 401) {
        response.cookies.delete("craves_access_token");
      }
      return response;
    }

    const kitchen = parseChefKitchen(await upstream.json().catch(() => null));
    if (!kitchen) {
      return NextResponse.json(
        {
          code: "INVALID_KITCHEN_RESPONSE",
        },
        { status: 502 },
      );
    }

    const response = NextResponse.json(kitchen);
    response.headers.set("Cache-Control", "no-store");
    return response;
  } catch (error) {
    const timedOut = error instanceof Error && error.name === "AbortError";
    return NextResponse.json(
      {
        code: timedOut ? "KITCHEN_TIMEOUT" : "KITCHEN_UNAVAILABLE",
      },
      { status: timedOut ? 504 : 503 },
    );
  } finally {
    clearTimeout(timeout);
  }
}

export async function GET(request: NextRequest) {
  return call(request, "GET");
}

export async function PUT(request: NextRequest) {
  if (!isSameOrigin(request)) {
    return NextResponse.json({ code: "ORIGIN_REJECTED" }, { status: 403 });
  }

  const input = parseChefKitchenInput(await request.json().catch(() => null));
  if (!input) {
    return NextResponse.json(
      {
        code: "INVALID_KITCHEN_PROFILE",
        message:
          "Complete the required kitchen fields. ACTIVE kitchens also require valid latitude and longitude coordinates.",
      },
      { status: 400 },
    );
  }

  return call(request, "PUT", input);
}
