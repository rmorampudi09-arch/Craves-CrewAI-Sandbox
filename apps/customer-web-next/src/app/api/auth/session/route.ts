import { NextRequest, NextResponse } from "next/server";
import { parseSessionExchange, publicAuthError } from "@/lib/auth-contract";
import { setSessionCookies } from "@/lib/auth-cookies";
import { isSameOrigin } from "@/lib/request-security";
import { apiBaseUrl } from "@/lib/server-api";

export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) {
    return NextResponse.json(
      { code: "ORIGIN_REJECTED", message: "Invalid sign-in origin." },
      { status: 403 },
    );
  }

  const input = (await request.json().catch(() => null)) as { firebaseIdToken?: unknown } | null;
  const firebaseIdToken = typeof input?.firebaseIdToken === "string" ? input.firebaseIdToken.trim() : "";

  if (firebaseIdToken.length < 100 || firebaseIdToken.length > 20_000) {
    return NextResponse.json(
      {
        code: "INVALID_FIREBASE_TOKEN",
        message: "Firebase verification is required.",
      },
      { status: 400 },
    );
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10_000);

  try {
    const upstream = await fetch(`${apiBaseUrl()}/auth/firebase/exchange`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify({ firebaseIdToken }),
      cache: "no-store",
      signal: controller.signal,
    });

    const raw = await upstream.json().catch(() => null);

    if (!upstream.ok) {
      return NextResponse.json(
        {
          code: "SIGN_IN_FAILED",
          message: publicAuthError(upstream.status),
        },
        {
          status: upstream.status,
          headers: { "Cache-Control": "no-store" },
        },
      );
    }

    const session = parseSessionExchange(raw);
    if (!session) {
      return NextResponse.json(
        {
          code: "INVALID_AUTH_RESPONSE",
          message: "Sign-in is temporarily unavailable.",
        },
        {
          status: 502,
          headers: { "Cache-Control": "no-store" },
        },
      );
    }

    const response = NextResponse.json(
      { identity: session.identity },
      { headers: { "Cache-Control": "no-store" } },
    );
    setSessionCookies(response, session);
    return response;
  } catch (error) {
    const timedOut = error instanceof Error && error.name === "AbortError";
    return NextResponse.json(
      {
        code: timedOut ? "AUTH_TIMEOUT" : "AUTH_UNAVAILABLE",
        message: timedOut
          ? "Sign-in timed out. Please try again."
          : "Sign-in is temporarily unavailable.",
      },
      {
        status: timedOut ? 504 : 503,
        headers: { "Cache-Control": "no-store" },
      },
    );
  } finally {
    clearTimeout(timeout);
  }
}
