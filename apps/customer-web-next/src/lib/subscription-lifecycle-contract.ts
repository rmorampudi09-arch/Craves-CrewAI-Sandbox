export type SubscriptionPlanPolicy = {
  customerPauseEnabled: boolean;
  customerResumeEnabled: boolean;
  customerCancelEnabled: boolean;
  customerSkipEnabled: boolean;
  pauseCutoffMinutes: number | null;
  resumeLeadMinutes: number | null;
  cancelCutoffMinutes: number | null;
  skipCutoffMinutes: number | null;
  holidayPolicyReference: string | null;
  unusedMealPolicyReference: string | null;
  refundPolicyReference: string | null;
};

export type SubscriptionOccurrence = {
  id: string;
  serviceDate: string;
  mealSlotCode: string;
  serviceAt: string;
  status: "BILLING_PENDING" | "PAYMENT_PENDING" | "READY_FOR_ORDER" | "ORDER_REQUESTED" | "ORDER_CREATED" | "SKIPPED" | "CANCELLED" | "FAILED";
  items: Array<{ menuItemId: string; quantity: number; sequenceNumber: number }>;
};

export type SubscriptionSkipRequest = {
  id: string;
  subscriptionId: string;
  serviceDate: string;
  status: "REQUESTED" | "APPLIED" | "REJECTED";
  reason: string | null;
  occurrenceId: string | null;
  createdAt: string;
  appliedAt: string | null;
  updatedAt: string;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const SLOT = /^[A-Z0-9][A-Z0-9_-]{0,39}$/;
const OCCURRENCE_STATUSES = new Set([
  "BILLING_PENDING", "PAYMENT_PENDING", "READY_FOR_ORDER", "ORDER_REQUESTED", "ORDER_CREATED", "SKIPPED", "CANCELLED", "FAILED",
]);
const SKIP_STATUSES = new Set(["REQUESTED", "APPLIED", "REJECTED"]);

function integerOrNull(value: unknown): number | null | undefined {
  if (value === null || value === undefined) return null;
  return Number.isInteger(value) && Number(value) >= 0 ? Number(value) : undefined;
}

function optionalText(value: unknown, max = 200): string | null | undefined {
  if (value === null || value === undefined || value === "") return null;
  if (typeof value !== "string") return undefined;
  const trimmed = value.trim();
  return trimmed && trimmed.length <= max ? trimmed : undefined;
}

function instant(value: unknown): string | null {
  return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null;
}

export function parseSubscriptionPlanPolicy(value: unknown): SubscriptionPlanPolicy | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const flags = [raw.customerPauseEnabled, raw.customerResumeEnabled, raw.customerCancelEnabled, raw.customerSkipEnabled];
  if (flags.some(flag => typeof flag !== "boolean")) return null;
  const pauseCutoffMinutes = integerOrNull(raw.pauseCutoffMinutes);
  const resumeLeadMinutes = integerOrNull(raw.resumeLeadMinutes);
  const cancelCutoffMinutes = integerOrNull(raw.cancelCutoffMinutes);
  const skipCutoffMinutes = integerOrNull(raw.skipCutoffMinutes);
  if ([pauseCutoffMinutes, resumeLeadMinutes, cancelCutoffMinutes, skipCutoffMinutes].some(value => value === undefined)) return null;
  const holidayPolicyReference = optionalText(raw.holidayPolicyReference);
  const unusedMealPolicyReference = optionalText(raw.unusedMealPolicyReference);
  const refundPolicyReference = optionalText(raw.refundPolicyReference);
  if ([holidayPolicyReference, unusedMealPolicyReference, refundPolicyReference].some(value => value === undefined)) return null;
  return {
    customerPauseEnabled: raw.customerPauseEnabled as boolean,
    customerResumeEnabled: raw.customerResumeEnabled as boolean,
    customerCancelEnabled: raw.customerCancelEnabled as boolean,
    customerSkipEnabled: raw.customerSkipEnabled as boolean,
    pauseCutoffMinutes: pauseCutoffMinutes as number | null,
    resumeLeadMinutes: resumeLeadMinutes as number | null,
    cancelCutoffMinutes: cancelCutoffMinutes as number | null,
    skipCutoffMinutes: skipCutoffMinutes as number | null,
    holidayPolicyReference: holidayPolicyReference as string | null,
    unusedMealPolicyReference: unusedMealPolicyReference as string | null,
    refundPolicyReference: refundPolicyReference as string | null,
  };
}

export function parseSubscriptionOccurrence(value: unknown): SubscriptionOccurrence | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  if (typeof raw.id !== "string" || !UUID.test(raw.id) || typeof raw.serviceDate !== "string" || !DATE.test(raw.serviceDate)) return null;
  if (typeof raw.mealSlotCode !== "string" || !SLOT.test(raw.mealSlotCode)) return null;
  const serviceAt = instant(raw.serviceAt);
  if (!serviceAt || typeof raw.status !== "string" || !OCCURRENCE_STATUSES.has(raw.status) || !Array.isArray(raw.items) || raw.items.length > 100) return null;
  const items = raw.items.map(item => {
    if (!item || typeof item !== "object") return null;
    const entry = item as Record<string, unknown>;
    if (typeof entry.menuItemId !== "string" || !UUID.test(entry.menuItemId) || !Number.isInteger(entry.quantity) || Number(entry.quantity) < 1 || !Number.isInteger(entry.sequenceNumber) || Number(entry.sequenceNumber) < 1) return null;
    return { menuItemId: entry.menuItemId, quantity: Number(entry.quantity), sequenceNumber: Number(entry.sequenceNumber) };
  });
  if (items.some(item => item === null)) return null;
  return { id: raw.id, serviceDate: raw.serviceDate, mealSlotCode: raw.mealSlotCode, serviceAt, status: raw.status as SubscriptionOccurrence["status"], items: items as SubscriptionOccurrence["items"] };
}

export function parseSubscriptionOccurrences(value: unknown): SubscriptionOccurrence[] | null {
  if (!Array.isArray(value) || value.length > 200) return null;
  const parsed = value.map(parseSubscriptionOccurrence);
  return parsed.some(item => item === null) ? null : parsed as SubscriptionOccurrence[];
}

export function parseSkipRequest(value: unknown): SubscriptionSkipRequest | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const createdAt = instant(raw.createdAt);
  const updatedAt = instant(raw.updatedAt);
  const appliedAt = raw.appliedAt == null ? null : instant(raw.appliedAt);
  const reason = optionalText(raw.reason, 1000);
  if (typeof raw.id !== "string" || !UUID.test(raw.id) || typeof raw.subscriptionId !== "string" || !UUID.test(raw.subscriptionId) || typeof raw.serviceDate !== "string" || !DATE.test(raw.serviceDate) || typeof raw.status !== "string" || !SKIP_STATUSES.has(raw.status) || !createdAt || !updatedAt || (raw.appliedAt != null && !appliedAt) || reason === undefined) return null;
  const occurrenceId = raw.occurrenceId == null ? null : raw.occurrenceId;
  if (occurrenceId !== null && (typeof occurrenceId !== "string" || !UUID.test(occurrenceId))) return null;
  return { id: raw.id, subscriptionId: raw.subscriptionId, serviceDate: raw.serviceDate, status: raw.status as SubscriptionSkipRequest["status"], reason, occurrenceId: occurrenceId as string | null, createdAt, appliedAt, updatedAt };
}
