export type PublicSubscriptionPlan = {
  id: string;
  planCode: string;
  name: string;
  description: string | null;
  billingPeriod: "WEEKLY" | "MONTHLY";
  amount: number;
  currency: string;
};

export type CustomerSubscriptionStatus =
  | "PENDING_PAYMENT"
  | "ACTIVE"
  | "PAUSED"
  | "PAYMENT_FAILED"
  | "EXPIRED"
  | "CANCELLED";

export type CustomerSubscription = {
  id: string;
  planId: string;
  status: CustomerSubscriptionStatus;
  startDate: string;
  endDate: string | null;
  nextServiceDate: string | null;
  deliveryAddressId: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CreateSubscriptionInput = {
  planId: string;
  startDate: string;
  deliveryAddressId: string | null;
  notes: string | null;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const CURRENCY = /^[A-Z]{3}$/;
const PLAN_PERIODS = new Set(["WEEKLY", "MONTHLY"]);
const SUBSCRIPTION_STATUSES = new Set(["PENDING_PAYMENT", "ACTIVE", "PAUSED", "PAYMENT_FAILED", "EXPIRED", "CANCELLED"]);

function text(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const result = value.trim();
  return result && result.length <= max ? result : null;
}
function optionalText(value: unknown, max: number): string | null { return value === null || value === undefined || value === "" ? null : text(value, max); }
function dateOnly(value: unknown): string | null { return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : null; }
function instant(value: unknown): string | null { return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null; }

export function indiaBusinessDate(now = new Date()): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Kolkata",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(now);
  const values = Object.fromEntries(parts.map(part => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

export function parsePublicSubscriptionPlan(value: unknown): PublicSubscriptionPlan | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const id = text(raw.id, 64); const planCode = text(raw.planCode, 80); const name = text(raw.name, 160);
  const billingPeriod = text(raw.billingPeriod, 20); const amount = typeof raw.amount === "number" ? raw.amount : Number(raw.amount);
  const currency = text(raw.currency, 3)?.toUpperCase() ?? null;
  if (!id || !UUID.test(id) || !planCode || !name || !billingPeriod || !PLAN_PERIODS.has(billingPeriod) || !Number.isFinite(amount) || amount < 0 || !currency || !CURRENCY.test(currency)) return null;
  return { id, planCode, name, description: optionalText(raw.description, 2000), billingPeriod: billingPeriod as PublicSubscriptionPlan["billingPeriod"], amount, currency };
}
export function parsePublicSubscriptionPlans(value: unknown): PublicSubscriptionPlan[] | null {
  if (!Array.isArray(value) || value.length > 500) return null;
  const parsed = value.map(parsePublicSubscriptionPlan); return parsed.some(item => item === null) ? null : parsed as PublicSubscriptionPlan[];
}

export function parseCustomerSubscription(value: unknown): CustomerSubscription | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const id = text(raw.id, 64); const planId = text(raw.planId, 64); const status = text(raw.status, 40);
  const startDate = dateOnly(raw.startDate); const createdAt = instant(raw.createdAt); const updatedAt = instant(raw.updatedAt);
  const deliveryAddressId = optionalText(raw.deliveryAddressId, 64);
  if (!id || !UUID.test(id) || !planId || !UUID.test(planId) || !status || !SUBSCRIPTION_STATUSES.has(status) || !startDate || !createdAt || !updatedAt || (deliveryAddressId && !UUID.test(deliveryAddressId))) return null;
  return { id, planId, status: status as CustomerSubscriptionStatus, startDate, endDate: raw.endDate == null ? null : dateOnly(raw.endDate), nextServiceDate: raw.nextServiceDate == null ? null : dateOnly(raw.nextServiceDate), deliveryAddressId, notes: optionalText(raw.notes, 2000), createdAt, updatedAt };
}
export function parseCustomerSubscriptions(value: unknown): CustomerSubscription[] | null {
  if (!Array.isArray(value) || value.length > 500) return null;
  const parsed = value.map(parseCustomerSubscription); return parsed.some(item => item === null) ? null : parsed as CustomerSubscription[];
}

export function parseCreateSubscriptionInput(value: unknown): CreateSubscriptionInput | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const planId = text(raw.planId, 64); const startDate = dateOnly(raw.startDate); const deliveryAddressId = optionalText(raw.deliveryAddressId, 64);
  if (!planId || !UUID.test(planId) || !startDate || (deliveryAddressId && !UUID.test(deliveryAddressId))) return null;
  if (startDate < indiaBusinessDate()) return null;
  return { planId, startDate, deliveryAddressId, notes: optionalText(raw.notes, 2000) };
}

export function parseSubscriptionReason(value: unknown): { reason: string | null } | null {
  if (value === null || value === undefined) return { reason: null };
  if (typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const reason = optionalText(raw.reason, 1000);
  return raw.reason && !reason ? null : { reason };
}
