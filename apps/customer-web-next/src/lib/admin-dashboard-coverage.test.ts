import assert from "node:assert/strict";
import test from "node:test";

import { parseAdminDashboardSummary } from "./admin-dashboard-contract.ts";

const summary = {
  generatedAt: "2026-08-18T08:30:00Z",
  metrics: {
    ordersCreated24h: 48,
    chefAcceptancePending: 6,
    preparing: 9,
    readyForPickup: 4,
    outForDelivery: 11,
    refundPending: 2,
    refundFailed: 1,
    delivered24h: 34
  },
  statusCounts: [
    { status: "CHEF_ACCEPTANCE_PENDING", count: 6 },
    { status: "PREPARING", count: 9 },
    { status: "OUT_FOR_DELIVERY", count: 11 }
  ],
  orderTrend: [
    { date: "2026-08-12", count: 31 },
    { date: "2026-08-13", count: 42 },
    { date: "2026-08-14", count: 48 }
  ],
  recentExceptions: [
    {
      orderId: "123e4567-e89b-42d3-a456-426614174000",
      kitchenName: null,
      status: "REFUND_PENDING",
      updatedAt: "2026-08-18T08:21:00Z",
      paymentReference: "must-be-dropped"
    }
  ]
};

test("admin dashboard contract allows operational counts and strips undocumented exception fields", () => {
  const parsed = parseAdminDashboardSummary(summary);
  assert.ok(parsed);
  assert.equal(parsed.metrics.outForDelivery, 11);
  assert.equal(parsed.recentExceptions[0]?.kitchenName, null);
  assert.equal("paymentReference" in (parsed.recentExceptions[0] ?? {}), false);
});

test("admin dashboard contract rejects oversized bounded collections", () => {
  const tooManyExceptions = {
    ...summary,
    recentExceptions: Array.from({ length: 11 }, (_, index) => ({
      orderId: `123e4567-e89b-42d3-a456-4266141740${String(index).padStart(2, "0")}`,
      kitchenName: `Kitchen ${index}`,
      status: "REFUND_PENDING",
      updatedAt: "2026-08-18T08:21:00Z"
    }))
  };

  assert.equal(parseAdminDashboardSummary(tooManyExceptions), null);
});
