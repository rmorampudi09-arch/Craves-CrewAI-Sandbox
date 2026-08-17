import assert from "node:assert/strict";
import test from "node:test";
import { parseAdminSubscriptionPlan, parseApprovedChefReferences } from "./admin-subscription-plan-contract.ts";

test("parses exact subscription plan states and amounts", () => {
  const parsed = parseAdminSubscriptionPlan({ id: "11111111-1111-4111-8111-111111111111", planCode: "WEEKLY-01", chefIdentityId: null, name: "Weekly meals", description: null, billingPeriod: "WEEKLY", amount: 1200, currency: "INR", status: "DRAFT", createdAt: "2026-07-30T00:00:00Z", updatedAt: "2026-07-30T00:00:00Z" });
  assert.equal(parsed?.amount, 1200);
  assert.equal(parseAdminSubscriptionPlan({ ...parsed, status: "DELETED" }), null);
});

test("approved chef selector accepts only approved applications", () => {
  const approved = parseApprovedChefReferences([{ id: "22222222-2222-4222-8222-222222222222", identityId: "33333333-3333-4333-8333-333333333333", firstName: "Home", lastName: "Chef", email: "chef@example.com", status: "APPROVED" }]);
  assert.equal(approved?.[0].displayName, "Home Chef");
  assert.equal(parseApprovedChefReferences([{ id: "22222222-2222-4222-8222-222222222222", identityId: "33333333-3333-4333-8333-333333333333", firstName: "Home", lastName: "Chef", email: "chef@example.com", status: "PENDING" }]), null);
});
