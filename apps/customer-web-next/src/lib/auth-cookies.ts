import type { NextResponse } from "next/server";
import type { CravesSessionExchange } from "./auth-contract";

const ACCESS_COOKIE = "craves_access_token";
const REFRESH_COOKIE = "craves_refresh_token";

export function setSessionCookies(response: NextResponse, session: CravesSessionExchange): void {
  const secure = process.env.NODE_ENV === "production";
  response.cookies.set(ACCESS_COOKIE, session.accessToken, {
    httpOnly: true,
    secure,
    sameSite: "lax",
    path: "/",
    maxAge: session.expiresIn,
  });
  const remaining = Math.max(60, Math.floor((Date.parse(session.refreshTokenExpiresAt) - Date.now()) / 1000));
  response.cookies.set(REFRESH_COOKIE, session.refreshToken, {
    httpOnly: true,
    secure,
    sameSite: "lax",
    path: "/api/auth",
    maxAge: Math.min(remaining, 31 * 24 * 60 * 60),
  });
}

export function clearSessionCookies(response: NextResponse): void {
  const secure = process.env.NODE_ENV === "production";
  response.cookies.set(ACCESS_COOKIE, "", { httpOnly: true, secure, sameSite: "lax", path: "/", maxAge: 0 });
  response.cookies.set(REFRESH_COOKIE, "", { httpOnly: true, secure, sameSite: "lax", path: "/api/auth", maxAge: 0 });
}
