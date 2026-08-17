import assert from "node:assert/strict";
import test from "node:test";
import {
  parseNotificationBacklog,
  parseNotificationBacklogQuery,
  parseNotificationRecoveryRequest,
  parseNotificationRecoveryResult
} from "./admin-notification-recovery-contract.ts";

const ID = "123e4567-e89b-42d3-a456-426614174000";

test("bounds backlog filters without permissive numeric coercion", () => {
  assert.deepEqual(
    parseNotificationBacklogQuery(new URLSearchParams("status=failed&limit=25")),
    { status: "FAILED", limit: 25 }
  );
  assert.equal(parseNotificationBacklogQuery(new URLSearchParams("status=SENT")), null);
  assert.equal(parseNotificationBacklogQuery(new URLSearchParams("limit=101")), null);
  assert.equal(parseNotificationBacklogQuery(new URLSearchParams("limit=1e2")), null);
  assert.equal(parseNotificationBacklogQuery(new URLSearchParams("limit=25.0")), null);
  assert.equal(parseNotificationBacklogQuery(new URLSearchParams("limit=")), null);
});

test("omits recipient identity and normalizes request UUIDs", () => {
  const result = parseNotificationBacklog([{
    requestId: ID.toUpperCase(),
    requestKey: "private-key",
    recipientIdentityId: ID,
    sourceService: "ORDER",
    eventType: "ORDER_CONFIRMED",
    channel: "PUSH",
    status: "DEAD_LETTER",
    attemptCount: 4,
    lastError: "provider unavailable",
    finalErrorCode: "MAX_ATTEMPTS"
  }]);
  assert.equal(result?.[0]?.requestId, ID);
  assert.equal(Object.hasOwn(result?.[0] ?? {}, "recipientIdentityId"), false);
  assert.equal(Object.hasOwn(result?.[0] ?? {}, "requestKey"), false);
});

test("requires exact RETRY confirmation", () => {
  assert.equal(
    parseNotificationRecoveryRequest({
      requestId: ID.toUpperCase(),
      reason: "Support case requires retry",
      confirmation: "RETRY"
    })?.requestId,
    ID
  );
  assert.equal(
    parseNotificationRecoveryRequest({
      requestId: ID,
      reason: "Support case requires retry",
      confirmation: "retry"
    }),
    null
  );
});

test("validates exact recovery state and audit identifiers", () => {
  const parsed = parseNotificationRecoveryResult({
    recoveryAuditId: ID.toUpperCase(),
    requestId: ID.toUpperCase(),
    previousStatus: "FAILED",
    newStatus: "PENDING",
    previousAttemptCount: 3,
    correlationId: ID.toUpperCase(),
    requeuedAt: "2026-07-31T00:00:00Z"
  });
  assert.equal(parsed?.newStatus, "PENDING");
  assert.equal(parsed?.requestId, ID);
  assert.equal(parsed?.correlationId, ID);
  assert.equal(parseNotificationRecoveryResult({
    recoveryAuditId: ID,
    requestId: ID,
    previousStatus: "FAILED",
    newStatus: "SENT",
    previousAttemptCount: 3,
    correlationId: ID,
    requeuedAt: "2026-07-31T00:00:00Z"
  }), null);
});
