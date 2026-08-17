import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

function source(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}

test("order history validates and sorts backend orders", () => {
  const page = source("../screens/OrderHistory/OrderHistory.tsx");
  assert.match(page, /fetch\("\/api\/orders"/);
  assert.match(page, /parseCustomerOrders\(raw\)/);
  assert.match(page, /new Date\(right\.createdAt\)\.getTime\(\)/);
  assert.doesNotMatch(page, /const orders = \[/);
});

test("each order links to its own tracking identity", () => {
  const page = source("../screens/OrderHistory/OrderHistory.tsx");
  assert.match(page, /to="\/tracking"/);
  assert.match(page, /search=\{\{ id: order\.id \}\}/);
  assert.match(page, /order\.items\.reduce/);
});

test("tracking validates both order and delivery projections", () => {
  const page = source("../screens/OrderTracking/OrderTracking.tsx");
  assert.match(page, /parseCustomerOrder\(orderRaw\)/);
  assert.match(page, /parseDeliveryStatusResponse\(deliveryRaw\)/);
  assert.match(page, /parsedDelivery\.orderId\.toLowerCase\(\)/);
  assert.match(page, /shouldAutoRefresh/);
});

test("provider link is HTTPS-sanitized by the delivery contract", () => {
  const contract = source("./delivery-status.ts");
  const page = source("../screens/OrderTracking/OrderTracking.tsx");
  assert.match(contract, /url\.protocol === "https:"/);
  assert.match(page, /rel="noopener noreferrer"/);
  assert.match(page, /delivery\?\.trackingUrl/);
});
