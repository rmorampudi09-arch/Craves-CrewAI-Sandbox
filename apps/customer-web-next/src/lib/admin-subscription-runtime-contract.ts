export type AdminSubscriptionScheduleItem = {
  id: string;
  menuItemId: string;
  quantity: number;
  isoDayOfWeek: number | null;
  dayOfMonth: number | null;
  mealSlotCode: string;
  serviceTime: string;
  sequenceNumber: number;
};

export type AdminSubscriptionSchedule = {
  planId: string;
  recurrenceType: "WEEKLY" | "MONTHLY";
  timezone: string;
  serviceTime: string;
  generationLeadHours: number;
  status: "DRAFT" | "ACTIVE" | "INACTIVE";
  version: number;
  items: AdminSubscriptionScheduleItem[];
  createdAt: string;
  updatedAt: string;
  activatedAt: string | null;
};

export type AdminSubscriptionPolicy = {
  id: string;
  planId: string;
  version: number;
  status: "DRAFT" | "ACTIVE" | "INACTIVE";
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
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  activatedAt: string | null;
};

export type AdminSubscriptionReadiness = {
  planId: string;
  activeSchedule: boolean;
  activePolicy: boolean;
  chefAssigned: boolean;
  readyForActivation: boolean;
};

export type AdminScheduleInput = {
  recurrenceType: "WEEKLY" | "MONTHLY";
  timezone: string;
  generationLeadHours: number;
  items: Array<{
    menuItemId: string;
    quantity: number;
    isoDayOfWeek: number | null;
    dayOfMonth: number | null;
    mealSlotCode: string;
    serviceTime: string;
    sequenceNumber: number;
  }>;
};

export type AdminPolicyInput = Pick<AdminSubscriptionPolicy,
  "customerPauseEnabled" | "customerResumeEnabled" | "customerCancelEnabled" | "customerSkipEnabled" |
  "pauseCutoffMinutes" | "resumeLeadMinutes" | "cancelCutoffMinutes" | "skipCutoffMinutes" |
  "holidayPolicyReference" | "unusedMealPolicyReference" | "refundPolicyReference" | "notes"
>;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SLOT = /^[A-Z0-9][A-Z0-9_-]{0,39}$/;
const TIME = /^([01]\d|2[0-3]):[0-5]\d(?::[0-5]\d(?:\.\d{1,9})?)?$/;
const SCHEDULE_STATUSES = new Set(["DRAFT", "ACTIVE", "INACTIVE"]);

function text(value: unknown, max: number): string | null { return typeof value === "string" && value.trim() && value.trim().length <= max ? value.trim() : null; }
function optionalText(value: unknown, max: number): string | null | undefined { return value == null || value === "" ? null : text(value, max) ?? undefined; }
function instant(value: unknown): string | null { return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null; }
function nonNegative(value: unknown): number | null | undefined { if (value == null) return null; return Number.isInteger(value) && Number(value) >= 0 ? Number(value) : undefined; }

function parseScheduleItem(value: unknown, recurrenceType: "WEEKLY" | "MONTHLY", withId: boolean): AdminSubscriptionScheduleItem | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const id = withId ? text(raw.id, 64) : UUID;
  const menuItemId = text(raw.menuItemId, 64);
  const mealSlotCode = text(raw.mealSlotCode, 40)?.toUpperCase() ?? null;
  const serviceTime = text(raw.serviceTime, 32);
  if ((withId && (!id || typeof id !== "string" || !UUID.test(id))) || !menuItemId || !UUID.test(menuItemId) || !mealSlotCode || !SLOT.test(mealSlotCode) || !serviceTime || !TIME.test(serviceTime)) return null;
  if (!Number.isInteger(raw.quantity) || Number(raw.quantity) < 1 || Number(raw.quantity) > 100 || !Number.isInteger(raw.sequenceNumber) || Number(raw.sequenceNumber) < 1 || Number(raw.sequenceNumber) > 100) return null;
  const isoDayOfWeek = raw.isoDayOfWeek == null ? null : Number(raw.isoDayOfWeek);
  const dayOfMonth = raw.dayOfMonth == null ? null : Number(raw.dayOfMonth);
  if (recurrenceType === "WEEKLY") {
    if (!Number.isInteger(isoDayOfWeek) || isoDayOfWeek! < 1 || isoDayOfWeek! > 7 || dayOfMonth !== null) return null;
  } else if (!Number.isInteger(dayOfMonth) || dayOfMonth! < 1 || dayOfMonth! > 28 || isoDayOfWeek !== null) return null;
  return { id: withId ? id as string : "00000000-0000-4000-8000-000000000000", menuItemId, quantity: Number(raw.quantity), isoDayOfWeek, dayOfMonth, mealSlotCode, serviceTime, sequenceNumber: Number(raw.sequenceNumber) };
}

export function parseAdminSchedule(value: unknown): AdminSubscriptionSchedule | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const planId = text(raw.planId, 64); const recurrenceType = raw.recurrenceType; const timezone = text(raw.timezone, 80); const serviceTime = text(raw.serviceTime, 32); const status = text(raw.status, 20);
  const createdAt = instant(raw.createdAt); const updatedAt = instant(raw.updatedAt); const activatedAt = raw.activatedAt == null ? null : instant(raw.activatedAt);
  if (!planId || !UUID.test(planId) || (recurrenceType !== "WEEKLY" && recurrenceType !== "MONTHLY") || !timezone || !serviceTime || !TIME.test(serviceTime) || !Number.isInteger(raw.generationLeadHours) || Number(raw.generationLeadHours) < 1 || Number(raw.generationLeadHours) > 168 || !status || !SCHEDULE_STATUSES.has(status) || !Number.isInteger(raw.version) || Number(raw.version) < 1 || !createdAt || !updatedAt || (raw.activatedAt != null && !activatedAt) || !Array.isArray(raw.items) || raw.items.length > 100) return null;
  const items = raw.items.map(item => parseScheduleItem(item, recurrenceType, true));
  if (items.some(item => item === null)) return null;
  return { planId, recurrenceType, timezone, serviceTime, generationLeadHours: Number(raw.generationLeadHours), status: status as AdminSubscriptionSchedule["status"], version: Number(raw.version), items: items as AdminSubscriptionScheduleItem[], createdAt, updatedAt, activatedAt };
}

export function parseAdminScheduleInput(value: unknown): AdminScheduleInput | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>; const recurrenceType = raw.recurrenceType; const timezone = text(raw.timezone, 80);
  if ((recurrenceType !== "WEEKLY" && recurrenceType !== "MONTHLY") || !timezone || !Number.isInteger(raw.generationLeadHours) || Number(raw.generationLeadHours) < 1 || Number(raw.generationLeadHours) > 168 || !Array.isArray(raw.items) || raw.items.length < 1 || raw.items.length > 100) return null;
  const items = raw.items.map(item => parseScheduleItem(item, recurrenceType, false));
  if (items.some(item => item === null)) return null;
  return {
    recurrenceType,
    timezone,
    generationLeadHours: Number(raw.generationLeadHours),
    items: (items as AdminSubscriptionScheduleItem[]).map(item => ({
      menuItemId: item.menuItemId,
      quantity: item.quantity,
      isoDayOfWeek: item.isoDayOfWeek,
      dayOfMonth: item.dayOfMonth,
      mealSlotCode: item.mealSlotCode,
      serviceTime: item.serviceTime,
      sequenceNumber: item.sequenceNumber,
    })),
  };
}

export function parseAdminPolicy(value: unknown): AdminSubscriptionPolicy | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>; const id = text(raw.id, 64); const planId = text(raw.planId, 64); const status = text(raw.status, 20);
  const createdAt = instant(raw.createdAt); const updatedAt = instant(raw.updatedAt); const activatedAt = raw.activatedAt == null ? null : instant(raw.activatedAt);
  const cutoffs = [nonNegative(raw.pauseCutoffMinutes), nonNegative(raw.resumeLeadMinutes), nonNegative(raw.cancelCutoffMinutes), nonNegative(raw.skipCutoffMinutes)];
  const refs = [optionalText(raw.holidayPolicyReference, 200), optionalText(raw.unusedMealPolicyReference, 200), optionalText(raw.refundPolicyReference, 200), optionalText(raw.notes, 4000)];
  if (!id || !UUID.test(id) || !planId || !UUID.test(planId) || !status || !SCHEDULE_STATUSES.has(status) || !Number.isInteger(raw.version) || Number(raw.version) < 1 || !createdAt || !updatedAt || (raw.activatedAt != null && !activatedAt) || [raw.customerPauseEnabled, raw.customerResumeEnabled, raw.customerCancelEnabled, raw.customerSkipEnabled].some(flag => typeof flag !== "boolean") || cutoffs.some(item => item === undefined) || refs.some(item => item === undefined)) return null;
  return { id, planId, version: Number(raw.version), status: status as AdminSubscriptionPolicy["status"], customerPauseEnabled: raw.customerPauseEnabled as boolean, customerResumeEnabled: raw.customerResumeEnabled as boolean, customerCancelEnabled: raw.customerCancelEnabled as boolean, customerSkipEnabled: raw.customerSkipEnabled as boolean, pauseCutoffMinutes: cutoffs[0] as number | null, resumeLeadMinutes: cutoffs[1] as number | null, cancelCutoffMinutes: cutoffs[2] as number | null, skipCutoffMinutes: cutoffs[3] as number | null, holidayPolicyReference: refs[0] as string | null, unusedMealPolicyReference: refs[1] as string | null, refundPolicyReference: refs[2] as string | null, notes: refs[3] as string | null, createdAt, updatedAt, activatedAt };
}

export function parseAdminPolicyInput(value: unknown): AdminPolicyInput | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const flags = [raw.customerPauseEnabled, raw.customerResumeEnabled, raw.customerCancelEnabled, raw.customerSkipEnabled];
  const cutoffs = [nonNegative(raw.pauseCutoffMinutes), nonNegative(raw.resumeLeadMinutes), nonNegative(raw.cancelCutoffMinutes), nonNegative(raw.skipCutoffMinutes)];
  const refs = [optionalText(raw.holidayPolicyReference, 200), optionalText(raw.unusedMealPolicyReference, 200), optionalText(raw.refundPolicyReference, 200), optionalText(raw.notes, 4000)];
  if (flags.some(flag => typeof flag !== "boolean") || cutoffs.some(item => item === undefined) || refs.some(item => item === undefined)) return null;
  if ((raw.customerPauseEnabled && cutoffs[0] == null) || (raw.customerResumeEnabled && cutoffs[1] == null) || (raw.customerCancelEnabled && cutoffs[2] == null) || (raw.customerSkipEnabled && cutoffs[3] == null)) return null;
  return { customerPauseEnabled: raw.customerPauseEnabled as boolean, customerResumeEnabled: raw.customerResumeEnabled as boolean, customerCancelEnabled: raw.customerCancelEnabled as boolean, customerSkipEnabled: raw.customerSkipEnabled as boolean, pauseCutoffMinutes: cutoffs[0] as number | null, resumeLeadMinutes: cutoffs[1] as number | null, cancelCutoffMinutes: cutoffs[2] as number | null, skipCutoffMinutes: cutoffs[3] as number | null, holidayPolicyReference: refs[0] as string | null, unusedMealPolicyReference: refs[1] as string | null, refundPolicyReference: refs[2] as string | null, notes: refs[3] as string | null };
}

export function parseAdminReadiness(value: unknown): AdminSubscriptionReadiness | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>; const planId = text(raw.planId, 64);
  if (!planId || !UUID.test(planId) || [raw.activeSchedule, raw.activePolicy, raw.chefAssigned, raw.readyForActivation].some(flag => typeof flag !== "boolean")) return null;
  return { planId, activeSchedule: raw.activeSchedule as boolean, activePolicy: raw.activePolicy as boolean, chefAssigned: raw.chefAssigned as boolean, readyForActivation: raw.readyForActivation as boolean };
}
