import assert from "node:assert/strict";
import test from "node:test";
import { parseCustomerOrder, parseCustomerOrders } from "./order-contract.ts";

const order = {
  id: "11111111-2222-4333-8444-555555555555",
  checkoutId: "21111111-2222-4333-8444-555555555555",
  customerIdentityId: "31111111-2222-4333-8444-555555555555",
  kitchenId: "41111111-2222-4333-8444-555555555555",
  kitchenName: "Home Kitchen",
  status: "PREPARING",
  currency: "INR",
  foodSubtotal: 250,
  platformFee: 0,
  taxAmount: 0,
  deliveryFee: 0,
  grandTotal: 250,
  chefResponseNote: "Preparing fresh",
  prepTimeMinutes: 30,
  pickupAddress: { contactPhoneNumber: "private", addressLine1: "private" },
  deliveryAddress: {
    recipientName: "Customer",
    contactPhoneNumber: "+919999999999",
    addressLine1: "1 Test Road",
    addressLine2: null,
    landmark: null,
    areaName: "Test Area",
    city: "Hyderabad",
    state: "Telangana",
    postalCode: "500001"
  },
  items: [{ id: "51111111-2222-4333-8444-555555555555", menuItemId: "61111111-2222-4333-8444-555555555555", itemName: "Meal", category: "Lunch", foodType: "VEG", unitPrice: 250, quantity: 1, lineTotal: 250 }],
  createdAt: "2026-07-30T00:00:00Z",
  updatedAt: "2026-07-30T00:10:00Z"
};

test("allow-lists customer order fields", () => {
  const parsed = parseCustomerOrder(order);
  assert.equal(parsed?.kitchenName, "Home Kitchen");
  assert.equal("customerIdentityId" in (parsed ?? {}), false);
  assert.equal("pickupAddress" in (parsed ?? {}), false);
  assert.equal("contactPhoneNumber" in (parsed?.deliveryAddress ?? {}), false);
});

test("rejects invalid states and identifiers", () => {
  assert.equal(parseCustomerOrder({ ...order, status: "PROVIDER_SECRET_STATE" }), null);
  assert.equal(parseCustomerOrder({ ...order, id: "bad" }), null);
});

test("validates complete order lists", () => {
  assert.equal(parseCustomerOrders([order])?.length, 1);
  assert.equal(parseCustomerOrders([order, { ...order, id: "bad" }]), null);
});
