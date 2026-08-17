import assert from "node:assert/strict";
import test from "node:test";
import { parseAdminIdentity } from "./admin-contract.ts";

test("enables an active ADMIN from the documented auth/me identity envelope", () => {
  const parsed = parseAdminIdentity({
    identity: {
      id: "11111111-1111-4111-8111-111111111111",
      firebaseUid: "private-firebase-uid",
      phoneNumber: "+919876543210",
      displayName: "Admin",
      email: "admin@example.com",
      status: "ACTIVE",
      roles: ["CUSTOMER", "ADMIN"]
    }
  });

  assert.equal(parsed?.adminEnabled, true);
  assert.equal(parsed?.displayName, "Admin");
  assert.equal(parsed?.email, "admin@example.com");
  assert.equal("id" in (parsed ?? {}), false);
  assert.equal("roles" in (parsed ?? {}), false);
  assert.equal("phoneNumber" in (parsed ?? {}), false);
  assert.equal("firebaseUid" in (parsed ?? {}), false);
});

test("retains compatibility with an older flat identity response", () => {
  const parsed = parseAdminIdentity({
    displayName: "Admin",
    email: "admin@example.com",
    status: "active",
    roles: ["admin"]
  });

  assert.equal(parsed?.adminEnabled, true);
  assert.equal(parsed?.status, "ACTIVE");
});

test("does not enable inactive or non-admin identity", () => {
  assert.equal(parseAdminIdentity({ identity: { status: "INACTIVE", roles: ["ADMIN"] } })?.adminEnabled, false);
  assert.equal(parseAdminIdentity({ identity: { status: "ACTIVE", roles: ["CUSTOMER"] } })?.adminEnabled, false);
});

test("rejects malformed or incomplete identity envelopes", () => {
  assert.equal(parseAdminIdentity(null), null);
  assert.equal(parseAdminIdentity({ identity: null }), null);
  assert.equal(parseAdminIdentity({ identity: { status: "ACTIVE", roles: [] } }), null);
});
