import assert from "node:assert/strict";
import test from "node:test";
import { isRequestOriginAllowed } from "./request-security.ts";

const publicOrigin =
  "https://ca-craves-web-prodlow.happysand-aedc7165.centralindia.azurecontainerapps.io";

test("accepts a direct same-origin request", () => {
  assert.equal(
    isRequestOriginAllowed({
      origin: "http://localhost:3000",
      requestUrl: "http://localhost:3000/api/auth/session",
    }),
    true,
  );
});

test("accepts the Azure Container Apps public origin behind its internal proxy", () => {
  assert.equal(
    isRequestOriginAllowed({
      origin: publicOrigin,
      requestUrl: "http://localhost:3000/api/auth/session",
      forwardedProto: "https",
      forwardedHost: "ca-craves-web-prodlow.happysand-aedc7165.centralindia.azurecontainerapps.io",
      host: "localhost:3000",
    }),
    true,
  );
});

test("uses the public Host header when Azure forwards only the protocol", () => {
  assert.equal(
    isRequestOriginAllowed({
      origin: publicOrigin,
      requestUrl: "http://localhost:3000/api/auth/session",
      forwardedProto: "https",
      host: "ca-craves-web-prodlow.happysand-aedc7165.centralindia.azurecontainerapps.io",
    }),
    true,
  );
});

test("uses the value added by the closest trusted proxy", () => {
  assert.equal(
    isRequestOriginAllowed({
      origin: publicOrigin,
      requestUrl: "http://localhost:3000/api/auth/session",
      forwardedProto: "http, https",
      forwardedHost:
        "attacker.example, ca-craves-web-prodlow.happysand-aedc7165.centralindia.azurecontainerapps.io",
    }),
    true,
  );
});

test("rejects a cross-origin request", () => {
  assert.equal(
    isRequestOriginAllowed({
      origin: "https://attacker.example",
      requestUrl: "http://localhost:3000/api/auth/session",
      forwardedProto: "https",
      forwardedHost: "ca-craves-web-prodlow.happysand-aedc7165.centralindia.azurecontainerapps.io",
    }),
    false,
  );
});

test("rejects missing or malformed origins", () => {
  assert.equal(
    isRequestOriginAllowed({
      origin: null,
      requestUrl: "http://localhost:3000/api/auth/session",
    }),
    false,
  );
  assert.equal(
    isRequestOriginAllowed({
      origin: "https://attacker.example/path",
      requestUrl: "http://localhost:3000/api/auth/session",
      forwardedProto: "https",
      forwardedHost: "attacker.example",
    }),
    false,
  );
});
