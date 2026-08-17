import assert from "node:assert/strict";
import test from "node:test";
import {
  parseChefEarning,
  parseChefEarnings,
} from "./chef-earnings-contract.ts";

const earning = {
  id: "11111111-2222-4333-8444-555555555555",
  chefIdentityId: "21111111-2222-4333-8444-555555555555",
  orderId: "31111111-2222-4333-8444-555555555555",
  orderSource: "ON_DEMAND",
  currency: "INR",
  grossAmount: 500,
  commissionAmount: 50,
  taxWithheldAmount: 10,
  adjustmentAmount: -5,
  netPayable: 435,
  allocationReference: "allocation-001",
  status: "APPROVED",
  reason: "Approved by finance",
  approvedAt: "2026-08-05T00:00:00Z",
  reversedAt: null,
  createdAt: "2026-08-05T00:00:00Z",
  updatedAt: "2026-08-05T00:00:00Z",
};

test("keeps audited amounts while removing chef identity", () => {
  const parsed = parseChefEarning(earning);
  assert.equal(parsed?.netPayable, 435);
  assert.equal(parsed?.status, "APPROVED");
  assert.equal("chefIdentityId" in (parsed ?? {}), false);
});

test("rejects arithmetic and status inconsistencies", () => {
  assert.equal(parseChefEarning({ ...earning, netPayable: 436 }), null);
  assert.equal(parseChefEarning({ ...earning, status: "PAID" }), null);
  assert.equal(parseChefEarning({ ...earning, commissionAmount: -1 }), null);
});

test("validates complete earning arrays", () => {
  assert.equal(parseChefEarnings([earning])?.length, 1);
  assert.equal(
    parseChefEarnings([earning, { ...earning, id: "not-a-uuid" }]),
    null,
  );
});
