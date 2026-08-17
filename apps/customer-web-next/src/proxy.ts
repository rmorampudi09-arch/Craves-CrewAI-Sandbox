import { NextRequest, NextResponse } from "next/server";

export function proxy(request: NextRequest) {
  if (process.env.CRAVES_ADMIN_PORTAL !== "true") return NextResponse.next();

  const path = request.nextUrl.pathname;
  const allowedPage = path === "/admin" || path.startsWith("/admin/") || path === "/sign-in";
  const allowedApi = path === "/api/admin" || path.startsWith("/api/admin/") || path === "/api/auth" || path.startsWith("/api/auth/");
  if (allowedPage || allowedApi) return NextResponse.next();

  if (path.startsWith("/api/")) {
    return NextResponse.json({ code: "ADMIN_PORTAL_ROUTE_NOT_AVAILABLE" }, { status: 404 });
  }
  return NextResponse.redirect(new URL("/admin", request.url));
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\..*).*)"]
};
