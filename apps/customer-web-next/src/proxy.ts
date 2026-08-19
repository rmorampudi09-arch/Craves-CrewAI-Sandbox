import { NextRequest, NextResponse } from "next/server";

const ADMIN_PATH_PREFIXES = ["/admin", "/api/admin"];
const CHEF_PATH_PREFIXES = ["/chef", "/api/chef"];

function hasPortalEnabled(path: string): boolean {
  if (ADMIN_PATH_PREFIXES.some((prefix) => path === prefix || path.startsWith(`${prefix}/`))) {
    return process.env.CRAVES_ADMIN_PORTAL === "true";
  }

  if (CHEF_PATH_PREFIXES.some((prefix) => path === prefix || path.startsWith(`${prefix}/`))) {
    return process.env.CRAVES_CHEF_PORTAL !== "false";
  }

  return true;
}

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  if (hasPortalEnabled(pathname)) {
    return NextResponse.next();
  }

  if (pathname.startsWith("/api/")) {
    return NextResponse.json(
      { code: "ROUTE_DISABLED", message: "This workspace is not enabled in the current deployment." },
      { status: 404 },
    );
  }

  if (pathname === "/sign-in") {
    return NextResponse.next();
  }

  const fallback = pathname.startsWith("/admin") ? "/home" : "/sign-in?next=/chef";
  return NextResponse.redirect(new URL(fallback, request.url));
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|.*\\..*).*)"],
};
