import assert from "node:assert/strict";
import test from "node:test";
import { parseAdminDashboardSummary } from "./admin-dashboard-contract.ts";

const valid = {
  generatedAt: "2026-08-04T12:00:00Z",
  metrics: {
    ordersCreated24h: 12, chefAcceptancePending: 2, preparing: 3, readyForPickup: 1,
    outForDelivery: 2, refundPending: 1, refundFailed: 0, delivered24h: 8
  },
  statusCounts: [{ status: "PREPARING", count: 3 }],
  orderTrend: [{ date: "2026-08-04", count: 12 }],
  recentExceptions: [{
    orderId: "99999999-9999-9999-9999-999999999999", kitchenName: "Lakshmi Kitchen",
    status: "REFUND_PENDING", updatedAt: "2026-08-04T11:55:00Z", customerIdentityId: "must-not-pass-through"
  }]
};

test("accepts the bounded operational dashboard contract", () => {
  const parsed = parseAdminDashboardSummary(valid);
  assert.equal(parsed?.metrics.ordersCreated24h, 12);
  assert.equal(parsed?.recentExceptions[0]?.orderId, "99999999-9999-9999-9999-999999999999");
  assert.equal(parsed?.recentExceptions[0]?.kitchenName, "Lakshmi Kitchen");
  assert.equal("customerIdentityId" in (parsed?.recentExceptions[0] ?? {}), false);
});

test("rejects invalid counts, identifiers, timestamps and statuses", () => {
  assert.equal(parseAdminDashboardSummary({ ...valid, metrics: { ...valid.metrics, refundFailed: -1 } }), null);
  assert.equal(parseAdminDashboardSummary({ ...valid, recentExceptions: [{ ...valid.recentExceptions[0], orderId: "bad" }] }), null);
  assert.equal(parseAdminDashboardSummary({ ...valid, statusCounts: [{ status: "UNDOCUMENTED", count: 1 }] }), null);
  assert.equal(parseAdminDashboardSummary({ ...valid, generatedAt: "not-a-time" }), null);
});
