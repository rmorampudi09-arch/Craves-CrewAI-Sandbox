import assert from "node:assert/strict";
import test from "node:test";
import { parseAddItemInput, parseCart, parseQuantityInput } from "./cart-contract.ts";

const item = {
  id: "11111111-1111-4111-8111-111111111111",
  menuItemId: "22222222-2222-4222-8222-222222222222",
  kitchenId: "33333333-3333-4333-8333-333333333333",
  itemName: "Andhra Meals",
  kitchenName: "Annapurna Home Kitchen",
  unitPrice: 180,
  currency: "INR",
  quantity: 2,
  lineTotal: 360,
  createdAt: "2026-07-30T00:00:00Z",
  updatedAt: "2026-07-30T00:00:00Z",
  providerPayload: { secret: true }
};

test("parses backend-owned cart totals and removes identity fields", () => {
  const parsed = parseCart({
    id: "44444444-4444-4444-8444-444444444444",
    customerIdentityId: "55555555-5555-4555-8555-555555555555",
    currency: "INR",
    items: [item],
    totals: { foodSubtotal: 360, currency: "INR" }
  });
  assert.ok(parsed);
  assert.equal(parsed.foodSubtotal, 360);
  assert.equal("customerIdentityId" in parsed, false);
  assert.equal("providerPayload" in parsed.items[0]!, false);
});

test("validates add and quantity inputs", () => {
  assert.deepEqual(parseAddItemInput({ menuItemId: item.menuItemId, quantity: 1 }), { menuItemId: item.menuItemId, quantity: 1 });
  assert.equal(parseAddItemInput({ menuItemId: "bad", quantity: 1 }), null);
  assert.deepEqual(parseQuantityInput({ quantity: 100 }), { quantity: 100 });
  assert.equal(parseQuantityInput({ quantity: 0 }), null);
});

test("rejects frontend and backend currency mismatch", () => {
  assert.equal(parseCart({
    id: "44444444-4444-4444-8444-444444444444",
    currency: "INR",
    items: [item],
    totals: { foodSubtotal: 360, currency: "USD" }
  }), null);
});
