import assert from "node:assert/strict";
import test from "node:test";
import { parsePaymentSession, parsePaymentStatus, parsePaymentVerification } from "./payment-contract.ts";

const paymentOrderId = "11111111-1111-4111-8111-111111111111";
const checkoutId = "22222222-2222-4222-8222-222222222222";

test("parses a Cashfree payment session without leaking internal identifiers", () => {
  const parsed = parsePaymentSession({
    paymentOrderId,
    checkoutId,
    cravesPaymentOrderRef: "CRV_PRIVATE",
    provider: "CASHFREE",
    providerOrderId: "provider-order",
    cfOrderId: "provider-cf-id",
    paymentSessionId: "session-token-for-cashfree-sdk",
    amount: 234,
    currency: "INR",
    status: "PAYMENT_PENDING",
    createdAt: "2026-07-30T00:00:00Z"
  });
  assert.ok(parsed);
  assert.equal("cashfreeOrderId" in parsed, false);
  assert.equal("cfOrderId" in parsed, false);
  assert.equal("cravesPaymentOrderRef" in parsed, false);
});

test("parses owned status without identity or provider status", () => {
  const parsed = parsePaymentStatus({
    paymentOrderId,
    checkoutId,
    provider: "RAZORPAY",
    providerOrderId: "order_test_123",
    providerPaymentId: "pay_test_123",
    customerIdentityId: "33333333-3333-4333-8333-333333333333",
    amount: 234,
    currency: "INR",
    status: "PAID",
    providerStatus: "SUCCESS",
    createdAt: "2026-07-30T00:00:00Z",
    updatedAt: "2026-07-30T00:01:00Z"
  });
  assert.ok(parsed);
  assert.equal("customerIdentityId" in parsed, false);
  assert.equal("providerStatus" in parsed, false);
});

test("rejects malformed or unknown payment state", () => {
  assert.equal(parsePaymentVerification({ paymentOrderId, status: "UNKNOWN" }), null);
  assert.equal(parsePaymentSession({ paymentOrderId, checkoutId, paymentSessionId: "", amount: 1, currency: "INR", status: "PAYMENT_PENDING", createdAt: "2026-07-30T00:00:00Z" }), null);
});

test("parses a Razorpay checkout session", () => {
  const parsed = parsePaymentSession({
    paymentOrderId,
    checkoutId,
    provider: "RAZORPAY",
    providerOrderId: "order_test_123",
    checkoutKeyId: "rzp_test_123",
    paymentSessionId: null,
    amount: 234,
    currency: "INR",
    status: "PAYMENT_PENDING",
    createdAt: "2026-07-30T00:00:00Z"
  });
  assert.ok(parsed);
  assert.equal(parsed.provider, "RAZORPAY");
  assert.equal(parsed.checkoutKeyId, "rzp_test_123");
});
