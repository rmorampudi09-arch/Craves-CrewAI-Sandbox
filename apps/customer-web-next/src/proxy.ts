import type { NextRequest } from "next/server";
import { NextResponse } from "next/server";
import { resolveRouteAccess, type Persona } from "./lib/route-authorization";

function readPersona(request: NextRequest): Persona | null {
  const raw = request.cookies.get("craves_persona")?.value?.toLowerCase();
  if (raw === "customer" || raw === "chef" || raw === "admin") return raw;
  return null;
}

export function middleware(request: NextRequest) {
  const persona = readPersona(request);
  const decision = resolveRouteAccess(request.nextUrl.pathname, persona);

  if (!decision.allowed && decision.redirectTo) {
    const target = new URL(decision.redirectTo, request.url);
    const response = NextResponse.redirect(target);
    response.headers.set("Cache-Control", decision.cacheControl);
    response.headers.set("X-Robots-Tag", "noindex, nofollow");
    return response;
  }

  const response = NextResponse.next();
  response.headers.set("Cache-Control", decision.cacheControl);
  if (decision.cacheControl === "private, no-store") {
    response.headers.set("X-Robots-Tag", "noindex, nofollow");
  }
  response.headers.set("X-Frame-Options", "DENY");
  response.headers.set("X-Content-Type-Options", "nosniff");
  return response;
}

export const config = {
  matcher: [
    "/admin/:path*",
    "/chef/:path*",
    "/addresses/:path*",
    "/cart/:path*",
    "/checkout/:path*",
    "/orders/:path*",
    "/notifications/:path*",
    "/payment/:path*",
    "/profile/:path*",
    "/subscriptions/:path*",
    "/tracking/:path*",
    "/wishlist/:path*",
    "/build-train",
  ],
};
