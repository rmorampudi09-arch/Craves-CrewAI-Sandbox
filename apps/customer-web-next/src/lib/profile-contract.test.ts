import assert from "node:assert/strict";
import test from "node:test";
import {
  parseCustomerProfile,
  parseProfileInput,
} from "./profile-contract.ts";

const profile = {
  id: "11111111-2222-4333-8444-555555555555",
  registeredPhoneNumber: "+919999999999",
  firstName: "Ravi",
  lastName: "Teja",
  email: "ravi@example.com",
  createdAt: "2026-08-05T09:00:00Z",
  updatedAt: "2026-08-05T09:10:00Z",
};

test("accepts separate first name, last name and optional email", () => {
  assert.deepEqual(
    parseProfileInput({
      firstName: "  Ravi  ",
      lastName: " Teja ",
      email: " ravi@example.com ",
    }),
    {
      firstName: "Ravi",
      lastName: "Teja",
      email: "ravi@example.com",
    },
  );

  assert.deepEqual(
    parseProfileInput({ firstName: "Ravi", lastName: "Teja", email: "" }),
    { firstName: "Ravi", lastName: "Teja", email: null },
  );
});

test("rejects missing names and malformed email", () => {
  assert.equal(
    parseProfileInput({ firstName: "", lastName: "Teja", email: null }),
    null,
  );
  assert.equal(
    parseProfileInput({ firstName: "Ravi", lastName: "", email: null }),
    null,
  );
  assert.equal(
    parseProfileInput({
      firstName: "Ravi",
      lastName: "Teja",
      email: "not-an-email",
    }),
    null,
  );
});

test("parses the authoritative customer profile response", () => {
  assert.deepEqual(parseCustomerProfile(profile), profile);
});

test("rejects profile responses with invalid identifiers or timestamps", () => {
  assert.equal(parseCustomerProfile({ ...profile, id: "not-a-uuid" }), null);
  assert.equal(parseCustomerProfile({ ...profile, updatedAt: "not-a-date" }), null);
});
