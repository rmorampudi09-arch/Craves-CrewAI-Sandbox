import assert from "node:assert/strict";
import test from "node:test";
import { parseAdminSubscriptionOperation } from "./admin-subscription-operation-contract.ts";

test("requires exact status and operational reason", () => {
  assert.deepEqual(parseAdminSubscriptionOperation({ status: "PAUSED", reason: "Customer support request" }), { status: "PAUSED", reason: "Customer support request" });
  assert.equal(parseAdminSubscriptionOperation({ status: "PAUSED", reason: "" }), null);
  assert.equal(parseAdminSubscriptionOperation({ status: "REFUNDED", reason: "Unsupported" }), null);
});
