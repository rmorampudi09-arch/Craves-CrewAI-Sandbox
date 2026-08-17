import assert from "node:assert/strict";
import test from "node:test";
import { parseCheckout, parseCheckoutInput } from "./checkout-contract.ts";

const order = {
  id: "11111111-1111-4111-8111-111111111111",
  checkoutId: "22222222-2222-4222-8222-222222222222",
  customerIdentityId: "33333333-3333-4333-8333-333333333333",
  kitchenId: "44444444-4444-4444-8444-444444444444",
  kitchenName: "Annapurna",
  status: "PAYMENT_PENDING",
  currency: "INR",
  foodSubtotal: 180,
  platformFee: 5,
  taxAmount: 9,
  deliveryFee: 40,
  grandTotal: 234,
  chefResponseNote: null,
  prepTimeMinutes: null,
  deliveryAddress: null,
  pickupAddress: { private: true },
  items: [
    {
      id: "55555555-5555-4555-8555-555555555555",
      menuItemId: "66666666-6666-4666-8666-666666666666",
      itemName: "Meals",
      category: "Lunch",
      foodType: "VEG",
      unitPrice: 180,
      quantity: 1,
      lineTotal: 180,
    },
  ],
  createdAt: "2026-07-30T00:00:00Z",
  updatedAt: "2026-07-30T00:00:00Z",
};

const deliveryAddressId = "88888888-8888-4888-8888-888888888888";
const seededChargePolicyId = "20000000-0000-0000-0000-000000000001";

function backendCheckout() {
  return {
    id: "22222222-2222-4222-8222-222222222222",
    customerIdentityId: "33333333-3333-4333-8333-333333333333",
    status: "PAYMENT_PENDING",
    currency: "INR",
    foodSubtotal: 180,
    platformFee: 5,
    taxAmount: 9,
    deliveryFee: 40,
    grandTotal: 234,
    chargePolicyId: seededChargePolicyId,
    deliveryAddressId,
    deliveryAddress: {
      sourceAddressId: deliveryAddressId,
      recipientName: "Ravi",
      contactPhoneNumber: "+919876543210",
      addressLine1: "Plot 1",
      areaName: "Kukatpally",
      city: "Hyderabad",
      state: "Telangana",
      postalCode: "500072",
    },
    orders: [order],
    createdAt: "2026-07-30T00:00:00Z",
  };
}

test("parses the seeded backend charge-policy UUID and checkout totals", () => {
  const parsed = parseCheckout(backendCheckout());
  assert.ok(parsed);
  assert.equal(parsed.chargePolicyId, seededChargePolicyId);
  assert.equal(parsed.grandTotal, 234);
  assert.equal("customerIdentityId" in parsed, false);
  assert.equal("pickupAddress" in parsed.orders[0]!, false);
});

test("still rejects malformed charge-policy identifiers", () => {
  assert.equal(
    parseCheckout({ ...backendCheckout(), chargePolicyId: "not-a-uuid" }),
    null,
  );
});

test("validates checkout input", () => {
  assert.deepEqual(
    parseCheckoutInput({ deliveryAddressId, note: "Ring bell" }),
    { deliveryAddressId, note: "Ring bell" },
  );
  assert.deepEqual(parseCheckoutInput({ deliveryAddressId, note: "   " }), {
    deliveryAddressId,
    note: null,
  });
  assert.deepEqual(parseCheckoutInput({ deliveryAddressId }), {
    deliveryAddressId,
    note: null,
  });
  assert.equal(
    parseCheckoutInput({ deliveryAddressId: "bad", note: "Ring bell" }),
    null,
  );
  assert.equal(
    parseCheckoutInput({ deliveryAddressId, note: "x".repeat(501) }),
    null,
  );
  assert.equal(parseCheckoutInput({ deliveryAddressId, note: 123 }), null);
});
