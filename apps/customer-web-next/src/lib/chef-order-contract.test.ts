import assert from "node:assert/strict";
import test from "node:test";
import {
  isCanonicalUuid,
  parseChefOrder,
  parseChefOrderResponse,
  parseChefOrders,
  parseChefOrdersResponse,
} from "./chef-order-contract.ts";

const order = {
  id: "11111111-2222-4333-8444-555555555555",
  checkoutId: "21111111-2222-4333-8444-555555555555",
  customerIdentityId: "31111111-2222-4333-8444-555555555555",
  kitchenId: "41111111-2222-4333-8444-555555555555",
  kitchenName: "Home Kitchen",
  status: "CHEF_ACCEPTANCE_PENDING",
  currency: "INR",
  foodSubtotal: 250,
  platformFee: 10,
  taxAmount: 12,
  deliveryFee: 30,
  grandTotal: 302,
  chefResponseNote: null,
  prepTimeMinutes: null,
  pickupAddress: { addressLine1: "private pickup" },
  deliveryAddress: {
    recipientName: "Customer",
    contactPhoneNumber: "+919999999999",
    addressLine1: "1 Test Road",
    city: "Hyderabad",
    state: "Telangana",
    postalCode: "500001",
  },
  items: [
    {
      id: "51111111-2222-4333-8444-555555555555",
      menuItemId: "61111111-2222-4333-8444-555555555555",
      itemName: "Meal",
      category: "Lunch",
      foodType: "VEG",
      unitPrice: 250,
      quantity: 1,
      lineTotal: 250,
    },
  ],
  createdAt: "2026-07-30T00:00:00Z",
  updatedAt: "2026-07-30T00:00:00Z",
};

test("keeps fulfillment address but removes identity/internal fields", () => {
  const parsed = parseChefOrder(order);
  assert.equal(parsed?.deliveryAddress?.contactPhoneNumber, "+919999999999");
  assert.equal("customerIdentityId" in (parsed ?? {}), false);
  assert.equal("checkoutId" in (parsed ?? {}), false);
  assert.equal("kitchenId" in (parsed ?? {}), false);
  assert.equal("pickupAddress" in (parsed ?? {}), false);
});

test("accepts canonical PostgreSQL UUIDs regardless of RFC version bits", () => {
  const historical = {
    ...order,
    id: "20000000-0000-0000-0000-000000000001",
    items: [
      {
        ...order.items[0],
        id: "30000000-0000-0000-0000-000000000001",
        menuItemId: "40000000-0000-0000-0000-000000000001",
      },
    ],
  };
  assert.equal(isCanonicalUuid(historical.id), true);
  assert.equal(parseChefOrder(historical)?.id, historical.id);
});

test("accepts nullable legacy snapshot labels from the Order Service schema", () => {
  const historical = {
    ...order,
    kitchenName: null,
    items: [{ ...order.items[0], category: null, foodType: null }],
  };
  const parsed = parseChefOrder(historical);
  assert.equal(parsed?.kitchenName, null);
  assert.equal(parsed?.items[0]?.category, null);
  assert.equal(parsed?.items[0]?.foodType, null);
});

test("uses createdAt for legacy rows without updatedAt", () => {
  const historical = { ...order, updatedAt: undefined };
  const parsed = parseChefOrder(historical);
  assert.equal(parsed?.updatedAt, order.createdAt);
});

test("accepts direct, Spring Page and named deployed list envelopes", () => {
  assert.equal(parseChefOrdersResponse([order])?.length, 1);
  assert.equal(parseChefOrdersResponse({ content: [order] })?.length, 1);
  assert.equal(parseChefOrdersResponse({ orders: [order] })?.length, 1);
  assert.equal(parseChefOrdersResponse({ data: [order] })?.length, 1);
  assert.equal(parseChefOrdersResponse({ data: { orders: [order] } })?.length, 1);
});

test("accepts direct and named deployed detail envelopes", () => {
  assert.equal(parseChefOrderResponse(order)?.id, order.id);
  assert.equal(parseChefOrderResponse({ order })?.id, order.id);
  assert.equal(parseChefOrderResponse({ data: order })?.id, order.id);
  assert.equal(parseChefOrderResponse({ data: { order } })?.id, order.id);
});

test("rejects unknown order status and malformed ids", () => {
  assert.equal(parseChefOrder({ ...order, status: "PROVIDER_INTERNAL" }), null);
  assert.equal(parseChefOrder({ ...order, id: "bad" }), null);
  assert.equal(isCanonicalUuid("20000000-0000-0000-0000-00000000000z"), false);
});

test("validates complete chef order arrays", () => {
  assert.equal(parseChefOrders([order])?.length, 1);
  assert.equal(parseChefOrders([order, { ...order, id: "bad" }]), null);
  assert.equal(parseChefOrdersResponse({ unexpected: [order] }), null);
});
