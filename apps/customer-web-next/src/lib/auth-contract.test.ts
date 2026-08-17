import assert from "node:assert/strict";
import test from "node:test";
import { parseSessionExchange, publicAuthError, safeReturnPath } from "./auth-contract.ts";

const valid = {
  accessToken: "header.payload.signature",
  refreshToken: "server-only-refresh-token",
  refreshTokenExpiresAt: "2026-09-02T08:00:00Z",
  expiresIn: 3600,
  identity: {
    id: "11111111-2222-4333-8444-555555555555",
    phoneNumber: "+919876543210",
    email: null,
    emailVerified: false,
    displayName: "Ravi",
    status: "ACTIVE",
    roles: ["CUSTOMER"]
  }
};

test("accepts a valid Craves server session", () => {
  const result = parseSessionExchange(valid);
  assert.equal(result?.identity.id, valid.identity.id);
  assert.equal(result?.accessToken, valid.accessToken);
  assert.equal(result?.refreshToken, valid.refreshToken);
});

test("rejects malformed identity and short sessions", () => {
  assert.equal(parseSessionExchange({ ...valid, expiresIn: 10 }), null);
  assert.equal(parseSessionExchange({ ...valid, identity: { ...valid.identity, id: "bad" } }), null);
  assert.equal(parseSessionExchange({ ...valid, identity: { ...valid.identity, roles: [] } }), null);
});

test("keeps return paths same-origin", () => {
  assert.equal(safeReturnPath("/orders/123/tracking"), "/orders/123/tracking");
  assert.equal(safeReturnPath("https://evil.example"), "/");
  assert.equal(safeReturnPath("//evil.example"), "/");
});

test("does not expose upstream error bodies", () => {
  assert.match(publicAuthError(401), /OTP session/i);
  assert.doesNotMatch(publicAuthError(500), /token|provider|stack/i);
});
