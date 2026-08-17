import assert from "node:assert/strict";
import test from "node:test";
import { parseCustomerNotification, parseCustomerNotifications } from "./notification-contract.ts";

const notice = {
  id: "11111111-2222-4333-8444-555555555555",
  title: "Delivery update",
  body: "Your order has been picked up.",
  noticeType: "DELIVERY_PICKED_UP",
  targetType: "ORDER",
  targetId: "21111111-2222-4333-8444-555555555555",
  readAt: null,
  createdAt: "2026-07-30T00:00:00Z",
  rawPayload: { providerDeliveryId: "private" },
  internalEventKey: "private"
};

test("allow-lists notification fields", () => {
  const parsed = parseCustomerNotification(notice);
  assert.equal(parsed?.title, notice.title);
  assert.equal("rawPayload" in (parsed ?? {}), false);
  assert.equal("internalEventKey" in (parsed ?? {}), false);
});

test("rejects invalid identifiers and timestamps", () => {
  assert.equal(parseCustomerNotification({ ...notice, id: "bad" }), null);
  assert.equal(parseCustomerNotification({ ...notice, createdAt: "not-a-date" }), null);
});

test("bounds inbox size", () => {
  assert.equal(parseCustomerNotifications([notice])?.length, 1);
  assert.equal(parseCustomerNotifications(Array.from({ length: 101 }, () => notice)), null);
});
