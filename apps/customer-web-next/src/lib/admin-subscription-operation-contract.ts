export type AdminSubscriptionStatus = "PENDING_PAYMENT" | "ACTIVE" | "PAUSED" | "PAYMENT_FAILED" | "EXPIRED" | "CANCELLED";

export type AdminSubscriptionSummary = {
  id: string;
  customerIdentityId: string;
  planId: string;
  chefIdentityId: string | null;
  status: AdminSubscriptionStatus;
  startDate: string;
  endDate: string | null;
  nextServiceDate: string | null;
  deliveryAddressId: string;
  createdAt: string;
  updatedAt: string;
};

export type AdminSubscriptionPage = {
  items: AdminSubscriptionSummary[];
  nextCreatedAt: string | null;
  nextId: string | null;
  hasMore: boolean;
};

export type AdminSubscriptionHistory = {
  id: string;
  oldStatus: AdminSubscriptionStatus | null;
  newStatus: AdminSubscriptionStatus;
  reason: string | null;
  actorIdentityId: string | null;
  createdAt: string;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const STATUSES = new Set(["PENDING_PAYMENT", "ACTIVE", "PAUSED", "PAYMENT_FAILED", "EXPIRED", "CANCELLED"]);

function text(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const result = value.trim();
  return result && result.length <= max ? result : null;
}

function uuid(value: unknown): string | null { const result = text(value, 64); return result && UUID.test(result) ? result : null; }
function optionalUuid(value: unknown): string | null | undefined { return value == null ? null : uuid(value) ?? undefined; }
function date(value: unknown): string | null { return typeof value === "string" && DATE.test(value) ? value : null; }
function optionalDate(value: unknown): string | null | undefined { return value == null ? null : date(value) ?? undefined; }
function instant(value: unknown): string | null { return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null; }
function optionalText(value: unknown, max: number): string | null | undefined { if (value == null || value === "") return null; return text(value, max) ?? undefined; }

export function parseAdminSubscriptionOperation(value: unknown): { status: AdminSubscriptionStatus; reason: string } | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const status = text(raw.status, 40);
  const reason = text(raw.reason, 1000);
  return status && STATUSES.has(status) && reason ? { status: status as AdminSubscriptionStatus, reason } : null;
}

export function parseAdminSubscriptionSummary(value: unknown): AdminSubscriptionSummary | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const id = uuid(raw.id), customerIdentityId = uuid(raw.customerIdentityId), planId = uuid(raw.planId), chefIdentityId = optionalUuid(raw.chefIdentityId), deliveryAddressId = uuid(raw.deliveryAddressId);
  const status = text(raw.status, 40), startDate = date(raw.startDate), endDate = optionalDate(raw.endDate), nextServiceDate = optionalDate(raw.nextServiceDate), createdAt = instant(raw.createdAt), updatedAt = instant(raw.updatedAt);
  if (!id || !customerIdentityId || !planId || chefIdentityId === undefined || !deliveryAddressId || !status || !STATUSES.has(status) || !startDate || endDate === undefined || nextServiceDate === undefined || !createdAt || !updatedAt) return null;
  return { id, customerIdentityId, planId, chefIdentityId, status: status as AdminSubscriptionStatus, startDate, endDate, nextServiceDate, deliveryAddressId, createdAt, updatedAt };
}

export function parseAdminSubscriptionPage(value: unknown): AdminSubscriptionPage | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  if (!Array.isArray(raw.items) || raw.items.length > 200 || typeof raw.hasMore !== "boolean") return null;
  const items = raw.items.map(parseAdminSubscriptionSummary);
  if (items.some(item => item === null)) return null;
  const nextCreatedAt = raw.nextCreatedAt == null ? null : instant(raw.nextCreatedAt);
  const nextId = raw.nextId == null ? null : uuid(raw.nextId);
  if ((raw.nextCreatedAt != null && !nextCreatedAt) || (raw.nextId != null && !nextId)) return null;
  if (raw.hasMore && (!nextCreatedAt || !nextId)) return null;
  return { items: items as AdminSubscriptionSummary[], nextCreatedAt, nextId, hasMore: raw.hasMore };
}

export function parseAdminSubscriptionHistory(value: unknown): AdminSubscriptionHistory[] | null {
  if (!Array.isArray(value) || value.length > 500) return null;
  const items = value.map(entry => {
    if (!entry || typeof entry !== "object") return null;
    const raw = entry as Record<string, unknown>;
    const id = uuid(raw.id), oldStatus = raw.oldStatus == null ? null : text(raw.oldStatus, 40), newStatus = text(raw.newStatus, 40), actorIdentityId = optionalUuid(raw.actorIdentityId), reason = optionalText(raw.reason, 1000), createdAt = instant(raw.createdAt);
    if (!id || (oldStatus !== null && !STATUSES.has(oldStatus)) || !newStatus || !STATUSES.has(newStatus) || actorIdentityId === undefined || reason === undefined || !createdAt) return null;
    return { id, oldStatus: oldStatus as AdminSubscriptionStatus | null, newStatus: newStatus as AdminSubscriptionStatus, reason, actorIdentityId, createdAt };
  });
  return items.some(item => item === null) ? null : items as AdminSubscriptionHistory[];
}
