import { describe, expect, it } from "vitest";

function hasRole(roles: string[], expected: string): boolean {
  return roles.map((role) => role.toUpperCase()).includes(expected);
}

describe("route authorization parity", () => {
  it("allows admin users into admin surfaces", () => {
    expect(hasRole(["CUSTOMER", "admin"], "ADMIN")).toBe(true);
  });

  it("denies non-admin users from admin surfaces", () => {
    expect(hasRole(["CUSTOMER", "CHEF"], "ADMIN")).toBe(false);
  });

  it("allows chefs and admins into chef surfaces", () => {
    expect(hasRole(["chef"], "CHEF") || hasRole(["chef"], "ADMIN")).toBe(true);
    expect(hasRole(["ADMIN"], "CHEF") || hasRole(["ADMIN"], "ADMIN")).toBe(true);
  });

  it("denies customer-only users from chef surfaces", () => {
    expect(hasRole(["CUSTOMER"], "CHEF") || hasRole(["CUSTOMER"], "ADMIN")).toBe(false);
  });
});
