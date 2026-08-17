import assert from "node:assert/strict";
import test from "node:test";
import { parseChefKitchen, parseChefKitchenInput } from "./chef-kitchen-contract.ts";

const kitchen = {
  id: "11111111-2222-4333-8444-555555555555",
  identityId: "21111111-2222-4333-8444-555555555555",
  kitchenName: "Home Kitchen",
  displayName: "Home Kitchen",
  addressLine1: "1 Test Road",
  city: "Hyderabad",
  state: "Telangana",
  status: "ACTIVE",
  createdAt: "2026-07-30T00:00:00Z",
  updatedAt: "2026-07-30T00:00:00Z"
};

test("removes backend identity ownership field", () => {
  const parsed = parseChefKitchen(kitchen);
  assert.equal(parsed?.kitchenName, "Home Kitchen");
  assert.equal("identityId" in (parsed ?? {}), false);
});

test("accepts suspended read state but blocks suspended writes", () => {
  assert.equal(parseChefKitchen({ ...kitchen, status: "SUSPENDED" })?.status, "SUSPENDED");
  assert.equal(parseChefKitchenInput({ ...kitchen, status: "SUSPENDED" }), null);
  assert.ok(parseChefKitchenInput({ ...kitchen, status: "INACTIVE" }));
});

test("requires paired coordinates", () => {
  assert.equal(parseChefKitchenInput({ ...kitchen, status: "DRAFT", latitude: 17.4, longitude: null }), null);
});
