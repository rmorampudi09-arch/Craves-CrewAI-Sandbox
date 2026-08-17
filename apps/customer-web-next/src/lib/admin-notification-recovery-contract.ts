export type NotificationBacklogStatus = "FAILED" | "DEAD_LETTER";

export type AdminNotificationBacklogItem = {
  requestId: string;
  sourceService: string;
  eventType: string;
  channel: string;
  status: NotificationBacklogStatus;
  attemptCount: number;
  nextAttemptAt: string | null;
  lastError: string | null;
  finalErrorCode: string | null;
  deadLetteredAt: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type AdminNotificationRecoveryResult = {
  recoveryAuditId: string;
  requestId: string;
  previousStatus: NotificationBacklogStatus;
  newStatus: "PENDING";
  previousAttemptCount: number;
  correlationId: string;
  requeuedAt: string;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const BACKLOG_STATUSES = new Set<NotificationBacklogStatus>(["FAILED", "DEAD_LETTER"]);

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

function dateTime(value: unknown): string | null {
  const candidate = nullableText(value, 80);
  return candidate && !Number.isNaN(Date.parse(candidate)) ? candidate : null;
}

function nonNegativeInteger(value: unknown): number | null {
  return typeof value === "number" && Number.isInteger(value) && value >= 0 ? value : null;
}

export function parseNotificationBacklogQuery(value: URLSearchParams): {
  status: NotificationBacklogStatus;
  limit: number;
} | null {
  const status = (value.get("status") ?? "DEAD_LETTER").trim().toUpperCase() as NotificationBacklogStatus;
  const limitText = (value.get("limit") ?? "50").trim();
  if (!/^\d{1,3}$/.test(limitText)) return null;
  const limit = Number(limitText);
  if (!BACKLOG_STATUSES.has(status) || !Number.isInteger(limit) || limit < 1 || limit > 100) return null;
  return { status, limit };
}

export function parseNotificationBacklog(value: unknown): AdminNotificationBacklogItem[] | null {
  if (!Array.isArray(value) || value.length > 100) return null;
  const output: AdminNotificationBacklogItem[] = [];
  for (const rawValue of value) {
    const raw = record(rawValue);
    const requestId = uuid(raw?.requestId);
    const sourceService = text(raw?.sourceService, 120);
    const eventType = text(raw?.eventType, 160);
    const channel = text(raw?.channel, 40);
    const status = text(raw?.status, 40) as NotificationBacklogStatus | null;
    const attemptCount = nonNegativeInteger(raw?.attemptCount);
    if (!requestId || !sourceService || !eventType || !channel || !status || !BACKLOG_STATUSES.has(status) || attemptCount == null) return null;
    output.push({
      requestId,
      sourceService,
      eventType,
      channel,
      status,
      attemptCount,
      nextAttemptAt: dateTime(raw?.nextAttemptAt),
      lastError: nullableText(raw?.lastError, 500),
      finalErrorCode: nullableText(raw?.finalErrorCode, 120),
      deadLetteredAt: dateTime(raw?.deadLetteredAt),
      createdAt: dateTime(raw?.createdAt),
      updatedAt: dateTime(raw?.updatedAt)
    });
  }
  return output;
}

export function parseNotificationRecoveryRequest(value: unknown): {
  requestId: string;
  reason: string;
  confirmation: "RETRY";
} | null {
  const raw = record(value);
  const requestId = uuid(raw?.requestId);
  const reason = text(raw?.reason, 500);
  const confirmation = text(raw?.confirmation, 20);
  if (!requestId || !reason || reason.length < 10 || confirmation !== "RETRY") return null;
  return { requestId, reason, confirmation: "RETRY" };
}

export function parseNotificationRecoveryResult(value: unknown): AdminNotificationRecoveryResult | null {
  const raw = record(value);
  const recoveryAuditId = uuid(raw?.recoveryAuditId);
  const requestId = uuid(raw?.requestId);
  const previousStatus = text(raw?.previousStatus, 40) as NotificationBacklogStatus | null;
  const newStatus = text(raw?.newStatus, 40);
  const previousAttemptCount = nonNegativeInteger(raw?.previousAttemptCount);
  const correlationId = uuid(raw?.correlationId);
  const requeuedAt = dateTime(raw?.requeuedAt);
  if (
    !recoveryAuditId || !requestId || !previousStatus || !BACKLOG_STATUSES.has(previousStatus) ||
    newStatus !== "PENDING" || previousAttemptCount == null || !correlationId || !requeuedAt
  ) return null;
  return { recoveryAuditId, requestId, previousStatus, newStatus: "PENDING", previousAttemptCount, correlationId, requeuedAt };
}
