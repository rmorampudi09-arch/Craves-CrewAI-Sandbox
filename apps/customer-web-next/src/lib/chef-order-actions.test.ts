import assert from "node:assert/strict";
import test from "node:test";

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function validAcceptance(prepTimeMinutes: unknown, actionId: unknown): boolean {
  return typeof prepTimeMinutes === "number" && Number.isInteger(prepTimeMinutes) && prepTimeMinutes >= 1 && prepTimeMinutes <= 1440 && typeof actionId === "string" && UUID.test(actionId);
}

function availableActions(status: string): string[] {
  if (status === "CHEF_ACCEPTANCE_PENDING") return ["accept", "reject"];
  if (status === "CHEF_ACCEPTED" || status === "PREPARING") return ["ready-for-pickup"];
  return [];
}

test("acceptance requires bounded prep time and UUID action identity", () => {
  assert.equal(validAcceptance(30, "11111111-2222-4333-8444-555555555555"), true);
  assert.equal(validAcceptance(0, "11111111-2222-4333-8444-555555555555"), false);
  assert.equal(validAcceptance(30, "not-a-uuid"), false);
});

test("offers only backend-supported transitions", () => {
  assert.deepEqual(availableActions("CHEF_ACCEPTANCE_PENDING"), ["accept", "reject"]);
  assert.deepEqual(availableActions("PREPARING"), ["ready-for-pickup"]);
  assert.deepEqual(availableActions("DELIVERED"), []);
  assert.deepEqual(availableActions("REFUND_PENDING"), []);
});
