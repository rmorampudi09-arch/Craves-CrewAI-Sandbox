import { describe, expect, it } from "vitest";
import { parseAdminIdentity } from "./admin-contract";

describe("parseAdminIdentity", () => {
  it("accepts nested identity payloads from auth service", () => {
    expect(
      parseAdminIdentity({
        identity: {
          displayName: "Ops Admin",
          email: "ops@craves.test",
          status: "active",
          roles: ["customer", "internal_admin"],
        },
      }),
    ).toEqual({
      displayName: "Ops Admin",
      email: "ops@craves.test",
      status: "ACTIVE",
      roles: ["CUSTOMER", "INTERNAL_ADMIN"],
      adminEnabled: true,
    });
  });

  it("rejects non-admin identities", () => {
    expect(
      parseAdminIdentity({
        identity: {
          displayName: "Customer",
          email: "customer@craves.test",
          status: "active",
          roles: ["customer"],
        },
      }),
    ).toEqual({
      displayName: "Customer",
      email: "customer@craves.test",
      status: "ACTIVE",
      roles: ["CUSTOMER"],
      adminEnabled: false,
    });
  });
});
