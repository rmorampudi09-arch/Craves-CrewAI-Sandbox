import assert from "node:assert/strict";
import test from "node:test";
import { parseSubscriptionPayment } from "./subscription-payment-contract.ts";

const valid = {
  id: "11111111-1111-4111-8111-111111111111",
  invoiceId: "22222222-2222-4222-8222-222222222222",
  subscriptionId: "33333333-3333-4333-8333-333333333333",
  cycleStart: "2026-08-12",
  cycleEnd: "2026-09-12",
  amount: 1499,
  currency: "INR",
  status: "PAYMENT_REQUESTED",
  provider: "CASHFREE",
  providerOrderId: null,
  providerPaymentId: null,
  checkoutKeyId: null,
  paymentSessionId: null,
  providerStatus: null,
  createdAt: "2026-08-12T06:30:00Z",
  updatedAt: "2026-08-12T06:30:00Z",
  paidAt: null,
};

test("parses a valid subscription payment response", () => {
  assert.deepEqual(parseSubscriptionPayment(valid), valid);
});

test("accepts a sandbox Cashfree payment session", () => {
  const parsed = parseSubscriptionPayment({
    ...valid,
    status: "PAYMENT_PENDING",
    paymentSessionId: "session_123",
    providerStatus: "ACTIVE",
  });
  assert.ok(parsed);
  assert.equal(parsed.paymentSessionId, "session_123");
});

test("accepts a Razorpay checkout order", () => {
  const parsed = parseSubscriptionPayment({
    ...valid,
    status: "PAYMENT_PENDING",
    provider: "RAZORPAY",
    providerOrderId: "order_test_123",
    checkoutKeyId: "rzp_test_123",
  });
  assert.ok(parsed);
  assert.equal(parsed.provider, "RAZORPAY");
});

test("rejects unsupported statuses and invalid amounts", () => {
  assert.equal(parseSubscriptionPayment({ ...valid, status: "REFUNDED" }), null);
  assert.equal(parseSubscriptionPayment({ ...valid, amount: 0 }), null);
  assert.equal(parseSubscriptionPayment({ ...valid, subscriptionId: "not-a-uuid" }), null);
});
