export type AdminAccountAction = "SUSPEND" | "REACTIVATE";

export type AdminAccountInterventionStatus = {
  interventionId: string | null;
  identityId: string;
  maskedPhoneNumber: string | null;
  status: string;
  tokenVersion: number;
  action: string | null;
  requestedStatus: string | null;
  providerStatus: string | null;
  providerAttemptCount: number;
  providerLastError: string | null;
  requestedAt: string | null;
  providerCompletedAt: string | null;
  correlationId: string | null;
  changed: boolean;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const ACCOUNT_STATUSES = new Set(["ACTIVE", "SUSPENDED"]);
const ACTIONS = new Set<AdminAccountAction>(["SUSPEND", "REACTIVATE"]);
const REQUESTED_STATUSES = new Set(["ACTIVE", "SUSPENDED"]);
const PROVIDER_STATUSES = new Set([
  "PENDING", "PROCESSING", "COMPLETED", "FAILED", "DEAD_LETTER", "SUPERSEDED", "NOT_REQUIRED"
]);

function record(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function text(value: unknown, max = 500): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.replace(/[\r\n]+/g, " ").trim();
  return normalized.length > 0 && normalized.length <= max ? normalized : null;
}

function nullableText(value: unknown, max = 500): string | null {
  return value == null ? null : text(value, max);
}

function uuid(value: unknown): string | null {
  const candidate = text(value, 64);
  return candidate && UUID.test(candidate) ? candidate.toLowerCase() : null;
}

function nonNegativeInteger(value: unknown): number | null {
  return typeof value === "number" && Number.isInteger(value) && value >= 0 ? value : null;
}

function dateTime(value: unknown): string | null {
  const candidate = nullableText(value, 80);
  return candidate && !Number.isNaN(Date.parse(candidate)) ? candidate : null;
}

function nullableEnum(value: unknown, allowed: Set<string>, max = 80): string | null {
  if (value == null) return null;
  const candidate = text(value, max);
  return candidate && allowed.has(candidate) ? candidate : null;
}

export function parseAdminAccountLookup(value: unknown): { identityId: string } | null {
  const root = record(value);
  const identityId = uuid(root?.identityId);
  return identityId ? { identityId } : null;
}

export function parseAdminAccountAction(value: unknown): {
  identityId: string;
  action: AdminAccountAction;
  reason: string;
  confirmation: string;
} | null {
  const root = record(value);
  const identityId = uuid(root?.identityId);
  const action = text(root?.action, 20) as AdminAccountAction | null;
  const reason = text(root?.reason, 500);
  const confirmation = text(root?.confirmation, 20);
  if (!identityId || !action || !ACTIONS.has(action)) return null;
  if (!reason || reason.length < 10 || confirmation !== action) return null;
  return { identityId, action, reason, confirmation };
}

export function parseAdminAccountInterventionStatus(value: unknown): AdminAccountInterventionStatus | null {
  const root = record(value);
  if (!root) return null;
  const identityId = uuid(root.identityId);
  const status = text(root.status, 80);
  const tokenVersion = nonNegativeInteger(root.tokenVersion);
  const providerAttemptCount = nonNegativeInteger(root.providerAttemptCount);
  const action = nullableEnum(root.action, ACTIONS as Set<string>, 40);
  const requestedStatus = nullableEnum(root.requestedStatus, REQUESTED_STATUSES, 80);
  const providerStatus = nullableEnum(root.providerStatus, PROVIDER_STATUSES, 80);
  const interventionId = root.interventionId == null ? null : uuid(root.interventionId);
  const correlationId = root.correlationId == null ? null : uuid(root.correlationId);
  if (
    !identityId || !status || !ACCOUNT_STATUSES.has(status) || tokenVersion == null ||
    providerAttemptCount == null || typeof root.changed !== "boolean"
  ) {
    return null;
  }
  if (root.interventionId != null && !interventionId) return null;
  if (root.action != null && !action) return null;
  if (root.requestedStatus != null && !requestedStatus) return null;
  if (root.providerStatus != null && !providerStatus) return null;
  if (root.correlationId != null && !correlationId) return null;
  return {
    interventionId,
    identityId,
    maskedPhoneNumber: nullableText(root.maskedPhoneNumber, 40),
    status,
    tokenVersion,
    action,
    requestedStatus,
    providerStatus,
    providerAttemptCount,
    providerLastError: nullableText(root.providerLastError, 500),
    requestedAt: dateTime(root.requestedAt),
    providerCompletedAt: dateTime(root.providerCompletedAt),
    correlationId,
    changed: root.changed
  };
}
