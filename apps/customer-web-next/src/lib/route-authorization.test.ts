import { describe, expect, it } from "vitest";
import { resolveRouteAccess } from "./route-authorization";

describe("resolveRouteAccess", () => {
  it("protects admin routes from unauthenticated access", () => {
    expect(resolveRouteAccess("/admin", null)).toEqual({
      allowed: false,
      redirectTo: "/sign-in?next=/admin",
      cacheControl: "private, no-store",
    });
  });

  it("protects admin routes from chef personas", () => {
    expect(resolveRouteAccess("/admin/operations", "chef")).toEqual({
      allowed: false,
      redirectTo: "/home",
      cacheControl: "private, no-store",
    });
  });

  it("allows chef workspace to admin and chef personas only", () => {
    expect(resolveRouteAccess("/chef/orders", "chef").allowed).toBe(true);
    expect(resolveRouteAccess("/chef/orders", "admin").allowed).toBe(true);
    expect(resolveRouteAccess("/chef/orders", "customer")).toEqual({
      allowed: false,
      redirectTo: "/home",
      cacheControl: "private, no-store",
    });
  });

  it("marks signed-in customer routes as private", () => {
    expect(resolveRouteAccess("/orders", "customer")).toEqual({
      allowed: true,
      cacheControl: "private, no-store",
    });
  });
});
