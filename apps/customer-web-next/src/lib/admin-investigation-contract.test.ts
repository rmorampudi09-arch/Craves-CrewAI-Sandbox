import assert from "node:assert/strict";
import test from "node:test";
import {
  parseAdminInvestigationRequest,
  parseAdminInvestigationResult
} from "./admin-investigation-contract.ts";

const ID = "11111111-1111-4111-8111-111111111111";
const CORRELATION = "22222222-2222-4222-8222-222222222222";

test("requires an exact resource, UUID and auditable reason", () => {
  assert.deepEqual(parseAdminInvestigationRequest({
    resource: "order",
    resourceId: ID,
    reason: "Investigating customer support case CRV-101"
  }), {
    resource: "order",
    resourceId: ID,
    reason: "Investigating customer support case CRV-101"
  });
  assert.equal(parseAdminInvestigationRequest({ resource: "order", resourceId: ID, reason: "short" }), null);
  assert.equal(parseAdminInvestigationRequest({ resource: "unknown", resourceId: ID, reason: "A valid long reason" }), null);
});

test("reduces order investigations to an explicit operator allow-list", () => {
  const parsed = parseAdminInvestigationResult("order", {
    order: {
      orderId: ID,
      status: "CHEF_ACCEPTED",
      currency: "INR",
      grandTotal: 425,
      kitchenName: "Home Kitchen",
      orderSource: "DIRECT",
      financialAllocationStatus: "NOT_APPLICABLE",
      maskedRecipientPhone: "******4321",
      areaName: "Madhapur",
      city: "Hyderabad",
      deliveryStatus: "ASSIGNED",
      createdAt: "2026-07-31T00:00:00Z",
      customerIdentityId: "33333333-3333-4333-8333-333333333333"
    },
    items: [{ menuItemId: ID }],
    statusHistory: [{ oldStatus: "PAID", newStatus: "CHEF_ACCEPTED", createdAt: "2026-07-31T00:10:00Z" }],
    deliveryHistory: [],
    refundEvents: []
  }, CORRELATION);

  assert.equal(parsed?.status, "CHEF_ACCEPTED");
  assert.equal(parsed?.summary.some(entry => entry.value.includes("33333333")), false);
  assert.equal(parsed?.timeline[0]?.detail, "PAID → CHEF_ACCEPTED");
});

test("rejects malformed upstream identifiers and correlation IDs", () => {
  assert.equal(parseAdminInvestigationResult("payment", {
    payment: { paymentOrderId: "not-a-uuid", status: "PAID" },
    attempts: [],
    events: []
  }, CORRELATION), null);
  assert.equal(parseAdminInvestigationResult("payment", {
    payment: { paymentOrderId: ID, status: "PAID" },
    attempts: [],
    events: []
  }, "bad-correlation"), null);
});

test("parses provider-neutral delivery evidence without raw payloads", () => {
  const parsed = parseAdminInvestigationResult("delivery-command", {
    command: {
      commandId: ID,
      orderId: CORRELATION,
      chefSubOrderId: "33333333-3333-4333-8333-333333333333",
      status: "RECONCILIATION_PENDING",
      attemptCount: 1,
      reconciliationAttemptCount: 2,
      reconciliationProviderId: "BORZO",
      readyAt: "2026-07-31T08:00:00Z",
      dispatchAt: "2026-07-31T07:50:00Z",
      createdAt: "2026-07-31T07:00:00Z",
      payload: { contactPhone: "+919999999999" }
    },
    job: null
  }, CORRELATION);

  assert.equal(parsed?.status, "RECONCILIATION_PENDING");
  assert.equal(JSON.stringify(parsed).includes("9999999999"), false);
});
