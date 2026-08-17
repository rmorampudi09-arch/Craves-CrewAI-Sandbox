export type ChefMealPlanPeriod = "WEEKLY" | "MONTHLY";
export type ChefMealPlanStatus = "DRAFT" | "PENDING_APPROVAL" | "ACTIVE" | "REJECTED" | "INACTIVE";

export type ChefMealPlan = {
  id: string;
  planCode: string;
  name: string;
  description: string | null;
  billingPeriod: ChefMealPlanPeriod;
  amount: number;
  currency: string;
  status: ChefMealPlanStatus;
  reviewReason: string | null;
  submittedAt: string | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ChefMealPlanInput = {
  name: string;
  description: string | null;
  billingPeriod: ChefMealPlanPeriod;
  amount: number;
  currency: string;
};

export type ChefMealScheduleItem = {
  id: string;
  menuItemId: string;
  quantity: number;
  isoDayOfWeek: number | null;
  dayOfMonth: number | null;
  mealSlotCode: string;
  serviceTime: string;
  sequenceNumber: number;
};

export type ChefMealSchedule = {
  planId: string;
  recurrenceType: ChefMealPlanPeriod;
  timezone: string;
  serviceTime: string;
  generationLeadHours: number;
  status: "DRAFT" | "ACTIVE" | "INACTIVE";
  version: number;
  items: ChefMealScheduleItem[];
  createdAt: string;
  updatedAt: string;
  activatedAt: string | null;
};

export type ChefMealScheduleInput = {
  recurrenceType: ChefMealPlanPeriod;
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

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const PERIODS = new Set(["WEEKLY", "MONTHLY"]);
const PLAN_STATUSES = new Set(["DRAFT", "PENDING_APPROVAL", "ACTIVE", "REJECTED", "INACTIVE"]);
const SCHEDULE_STATUSES = new Set(["DRAFT", "ACTIVE", "INACTIVE"]);
const SLOT = /^[A-Z0-9][A-Z0-9_-]{0,39}$/;
const TIME = /^([01]\d|2[0-3]):[0-5]\d(?::[0-5]\d(?:\.\d{1,9})?)?$/;

function text(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const result = value.trim();
  return result && result.length <= max ? result : null;
}
function optionalText(value: unknown, max: number): string | null {
  return value == null || value === "" ? null : text(value, max);
}
function instant(value: unknown): string | null {
  return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null;
}

export function parseChefMealPlan(value: unknown): ChefMealPlan | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const id = text(raw.id, 64), planCode = text(raw.planCode, 80), name = text(raw.name, 160);
  const billingPeriod = text(raw.billingPeriod, 20), currency = text(raw.currency, 3)?.toUpperCase() ?? null;
  const status = text(raw.status, 40), amount = typeof raw.amount === "number" ? raw.amount : Number(raw.amount);
  const createdAt = instant(raw.createdAt), updatedAt = instant(raw.updatedAt);
  const submittedAt = raw.submittedAt == null ? null : instant(raw.submittedAt);
  const reviewedAt = raw.reviewedAt == null ? null : instant(raw.reviewedAt);
  if (!id || !UUID.test(id) || !planCode || !name || !billingPeriod || !PERIODS.has(billingPeriod) || !currency || !/^[A-Z]{3}$/.test(currency) || !status || !PLAN_STATUSES.has(status) || !Number.isFinite(amount) || amount < 0 || !createdAt || !updatedAt || (raw.submittedAt != null && !submittedAt) || (raw.reviewedAt != null && !reviewedAt)) return null;
  return { id, planCode, name, description: optionalText(raw.description, 2000), billingPeriod: billingPeriod as ChefMealPlanPeriod, amount, currency, status: status as ChefMealPlanStatus, reviewReason: optionalText(raw.reviewReason, 1000), submittedAt, reviewedAt, createdAt, updatedAt };
}

export function parseChefMealPlans(value: unknown): ChefMealPlan[] | null {
  if (!Array.isArray(value) || value.length > 500) return null;
  const plans = value.map(parseChefMealPlan);
  return plans.some(plan => plan === null) ? null : plans as ChefMealPlan[];
}

export function parseChefMealPlanInput(value: unknown): ChefMealPlanInput | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const name = text(raw.name, 160), billingPeriod = text(raw.billingPeriod, 20), currency = text(raw.currency, 3)?.toUpperCase() ?? null;
  const amount = typeof raw.amount === "number" ? raw.amount : Number(raw.amount);
  if (!name || !billingPeriod || !PERIODS.has(billingPeriod) || !currency || !/^[A-Z]{3}$/.test(currency) || !Number.isFinite(amount) || amount < 0) return null;
  return { name, description: optionalText(raw.description, 2000), billingPeriod: billingPeriod as ChefMealPlanPeriod, amount, currency };
}

function scheduleItem(value: unknown, recurrence: ChefMealPlanPeriod, withId: boolean): ChefMealScheduleItem | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const id = withId ? text(raw.id, 64) : "00000000-0000-4000-8000-000000000000";
  const menuItemId = text(raw.menuItemId, 64), mealSlotCode = text(raw.mealSlotCode, 40)?.toUpperCase() ?? null, serviceTime = text(raw.serviceTime, 32);
  if (!id || !UUID.test(id) || !menuItemId || !UUID.test(menuItemId) || !mealSlotCode || !SLOT.test(mealSlotCode) || !serviceTime || !TIME.test(serviceTime) || !Number.isInteger(raw.quantity) || Number(raw.quantity) < 1 || Number(raw.quantity) > 100 || !Number.isInteger(raw.sequenceNumber) || Number(raw.sequenceNumber) < 1 || Number(raw.sequenceNumber) > 100) return null;
  const isoDayOfWeek = raw.isoDayOfWeek == null ? null : Number(raw.isoDayOfWeek);
  const dayOfMonth = raw.dayOfMonth == null ? null : Number(raw.dayOfMonth);
  if (recurrence === "WEEKLY") {
    if (!Number.isInteger(isoDayOfWeek) || isoDayOfWeek! < 1 || isoDayOfWeek! > 7 || dayOfMonth !== null) return null;
  } else if (!Number.isInteger(dayOfMonth) || dayOfMonth! < 1 || dayOfMonth! > 28 || isoDayOfWeek !== null) return null;
  return { id, menuItemId, quantity: Number(raw.quantity), isoDayOfWeek, dayOfMonth, mealSlotCode, serviceTime, sequenceNumber: Number(raw.sequenceNumber) };
}

export function parseChefMealSchedule(value: unknown): ChefMealSchedule | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const planId = text(raw.planId, 64), recurrenceType = text(raw.recurrenceType, 20), timezone = text(raw.timezone, 80), serviceTime = text(raw.serviceTime, 32), status = text(raw.status, 20);
  const createdAt = instant(raw.createdAt), updatedAt = instant(raw.updatedAt), activatedAt = raw.activatedAt == null ? null : instant(raw.activatedAt);
  if (!planId || !UUID.test(planId) || !recurrenceType || !PERIODS.has(recurrenceType) || !timezone || !serviceTime || !TIME.test(serviceTime) || !status || !SCHEDULE_STATUSES.has(status) || !Number.isInteger(raw.generationLeadHours) || Number(raw.generationLeadHours) < 1 || Number(raw.generationLeadHours) > 168 || !Number.isInteger(raw.version) || Number(raw.version) < 1 || !createdAt || !updatedAt || (raw.activatedAt != null && !activatedAt) || !Array.isArray(raw.items) || raw.items.length > 100) return null;
  const items = raw.items.map(item => scheduleItem(item, recurrenceType as ChefMealPlanPeriod, true));
  if (items.some(item => item === null)) return null;
  return { planId, recurrenceType: recurrenceType as ChefMealPlanPeriod, timezone, serviceTime, generationLeadHours: Number(raw.generationLeadHours), status: status as ChefMealSchedule["status"], version: Number(raw.version), items: items as ChefMealScheduleItem[], createdAt, updatedAt, activatedAt };
}

export function parseChefMealScheduleInput(value: unknown): ChefMealScheduleInput | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const recurrenceType = text(raw.recurrenceType, 20), timezone = text(raw.timezone, 80);
  if (!recurrenceType || !PERIODS.has(recurrenceType) || !timezone || !Number.isInteger(raw.generationLeadHours) || Number(raw.generationLeadHours) < 1 || Number(raw.generationLeadHours) > 168 || !Array.isArray(raw.items) || raw.items.length < 1 || raw.items.length > 100) return null;
  const parsed = raw.items.map(item => scheduleItem(item, recurrenceType as ChefMealPlanPeriod, false));
  if (parsed.some(item => item === null)) return null;
  const items = (parsed as ChefMealScheduleItem[]).map(item => ({
    menuItemId: item.menuItemId,
    quantity: item.quantity,
    isoDayOfWeek: item.isoDayOfWeek,
    dayOfMonth: item.dayOfMonth,
    mealSlotCode: item.mealSlotCode,
    serviceTime: item.serviceTime,
    sequenceNumber: item.sequenceNumber,
  }));
  return { recurrenceType: recurrenceType as ChefMealPlanPeriod, timezone, generationLeadHours: Number(raw.generationLeadHours), items };
}
