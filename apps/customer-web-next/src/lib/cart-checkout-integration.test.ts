import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

function source(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}

test("cart has no demo or local mutation fallback", () => {
  const cart = source("../services/api/cravesCart.ts");
  assert.match(cart, /fetch\(path/);
  assert.match(cart, /\/api\/cart/);
  assert.match(cart, /throw error/);
  assert.doesNotMatch(cart, /demo|localStorage|sessionStorage|fallback/i);
  assert.doesNotMatch(cart, /crypto\.randomUUID/);
});

test("cart validates with the backend before address selection", () => {
  const page = source("../screens/Cart/Cart.tsx");
  assert.match(page, /await validateCart\(\)/);
  assert.match(page, /navigate\(\{ to: "\/payment" \}\)/);
  assert.match(page, /cartCurrency\(\)/);
});

test("checkout uses parsed active saved addresses and backend checkout", () => {
  const page = source("../screens/Checkout/Checkout.tsx");
  assert.match(page, /parseCustomerAddresses\(raw\)/);
  assert.match(page, /filter\(\(address\) => address\.active\)/);
  assert.match(page, /fetch\("\/api\/checkout"/);
  assert.match(page, /deliveryAddressId: selectedId/);
  assert.match(page, /parseCheckout\(raw\)/);
  assert.doesNotMatch(page, /deliveryFee\s*=|platformFee\s*=|taxAmount\s*=/);
});

test("Razorpay payment is contract validated and backend verified", () => {
  const payment = source("../components/checkout/RazorpayPayment.tsx");
  assert.match(payment, /https:\/\/checkout\.razorpay\.com\/v1\/checkout\.js/);
  assert.match(payment, /parsePaymentSession\(raw\)/);
  assert.match(payment, /parsePaymentStatus\(raw\)/);
  assert.match(payment, /parsePaymentVerification\(raw\)/);
  assert.match(payment, /\/api\/payments\/orders/);
  assert.match(payment, /\/verify/);
  assert.doesNotMatch(
    payment,
    /<(input|textarea)[^>]*(name|id|autoComplete)=[^>]*(card|cvv|upi[-_ ]?pin)/i,
  );
});
