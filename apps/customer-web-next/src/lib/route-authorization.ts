export type Persona = "public" | "customer" | "chef" | "admin";

export type RouteAccessDecision = {
  allowed: boolean;
  redirectTo?: string;
  cacheControl: "public, max-age=300" | "private, no-store";
};

const publicPrefixes = [
  "/",
  "/home",
  "/discover",
  "/contact",
  "/privacy",
  "/terms",
  "/refunds-cancellations",
  "/security",
  "/build-train",
  "/sign-in",
];

function matchesPrefix(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

export function resolveRouteAccess(pathname: string, persona: Persona | null): RouteAccessDecision {
  if (publicPrefixes.some((prefix) => matchesPrefix(pathname, prefix))) {
    return { allowed: true, cacheControl: "public, max-age=300" };
  }

  if (matchesPrefix(pathname, "/admin")) {
    return persona === "admin"
      ? { allowed: true, cacheControl: "private, no-store" }
      : { allowed: false, redirectTo: persona ? "/home" : "/sign-in?next=/admin", cacheControl: "private, no-store" };
  }

  if (matchesPrefix(pathname, "/chef")) {
    return persona === "chef" || persona === "admin"
      ? { allowed: true, cacheControl: "private, no-store" }
      : { allowed: false, redirectTo: persona ? "/home" : "/sign-in?next=/chef", cacheControl: "private, no-store" };
  }

  if (
    ["/addresses", "/cart", "/checkout", "/orders", "/notifications", "/profile", "/subscriptions", "/tracking", "/wishlist", "/payment"].some(
      (prefix) => matchesPrefix(pathname, prefix),
    )
  ) {
    return persona
      ? { allowed: true, cacheControl: "private, no-store" }
      : { allowed: false, redirectTo: "/sign-in", cacheControl: "private, no-store" };
  }

  return { allowed: true, cacheControl: "public, max-age=300" };
}
