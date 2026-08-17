export type AdminPlanPeriod = "WEEKLY" | "MONTHLY";
export type AdminPlanStatus = "DRAFT" | "PENDING_APPROVAL" | "ACTIVE" | "REJECTED" | "INACTIVE";

export type AdminSubscriptionPlan = {
  id: string;
  planCode: string;
  chefIdentityId: string | null;
  name: string;
  description: string | null;
  billingPeriod: AdminPlanPeriod;
  amount: number;
  currency: string;
  status: AdminPlanStatus;
  createdAt: string;
  updatedAt: string;
};

export type ApprovedChefReference = {
  identityId: string;
  applicationId: string;
  displayName: string;
  email: string;
};

export type AdminSubscriptionPlanInput = {
  planCode: string;
  chefIdentityId: string | null;
  name: string;
  description: string | null;
  billingPeriod: AdminPlanPeriod;
  amount: number;
  currency: string;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const PERIODS = new Set(["WEEKLY", "MONTHLY"]);
const STATUSES = new Set(["DRAFT", "PENDING_APPROVAL", "ACTIVE", "REJECTED", "INACTIVE"]);

function text(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const result = value.trim();
  return result && result.length <= max ? result : null;
}
function optionalText(value: unknown, max: number): string | null { return value == null || value === "" ? null : text(value, max); }
function instant(value: unknown): string | null { return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null; }

export function parseAdminSubscriptionPlan(value: unknown): AdminSubscriptionPlan | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const id = text(raw.id, 64); const planCode = text(raw.planCode, 80); const chefIdentityId = optionalText(raw.chefIdentityId, 64);
  const name = text(raw.name, 160); const billingPeriod = text(raw.billingPeriod, 20); const amount = typeof raw.amount === "number" ? raw.amount : Number(raw.amount);
  const currency = text(raw.currency, 3)?.toUpperCase() ?? null; const status = text(raw.status, 40); const createdAt = instant(raw.createdAt); const updatedAt = instant(raw.updatedAt);
  if (!id || !UUID.test(id) || !planCode || (chefIdentityId && !UUID.test(chefIdentityId)) || !name || !billingPeriod || !PERIODS.has(billingPeriod) || !Number.isFinite(amount) || amount < 0 || !currency || !/^[A-Z]{3}$/.test(currency) || !status || !STATUSES.has(status) || !createdAt || !updatedAt) return null;
  return { id, planCode, chefIdentityId, name, description: optionalText(raw.description, 2000), billingPeriod: billingPeriod as AdminPlanPeriod, amount, currency, status: status as AdminPlanStatus, createdAt, updatedAt };
}
export function parseAdminSubscriptionPlans(value: unknown): AdminSubscriptionPlan[] | null {
  if (!Array.isArray(value) || value.length > 2000) return null;
  const parsed = value.map(parseAdminSubscriptionPlan); return parsed.some(item => item === null) ? null : parsed as AdminSubscriptionPlan[];
}

export function parseApprovedChefReferences(value: unknown): ApprovedChefReference[] | null {
  if (!Array.isArray(value) || value.length > 2000) return null;
  const parsed = value.map(item => {
    if (!item || typeof item !== "object") return null;
    const raw = item as Record<string, unknown>; const identityId = text(raw.identityId, 64); const applicationId = text(raw.id, 64);
    const firstName = text(raw.firstName, 120); const lastName = text(raw.lastName, 120); const email = text(raw.email, 320); const status = text(raw.status, 40);
    if (!identityId || !UUID.test(identityId) || !applicationId || !UUID.test(applicationId) || !firstName || !lastName || !email || status !== "APPROVED") return null;
    return { identityId, applicationId, displayName: `${firstName} ${lastName}`, email };
  });
  return parsed.some(item => item === null) ? null : parsed as ApprovedChefReference[];
}

export function parseAdminSubscriptionPlanInput(value: unknown): AdminSubscriptionPlanInput | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const planCode = text(raw.planCode, 80); const chefIdentityId = optionalText(raw.chefIdentityId, 64); const name = text(raw.name, 160);
  const billingPeriod = text(raw.billingPeriod, 20); const amount = typeof raw.amount === "number" ? raw.amount : Number(raw.amount); const currency = text(raw.currency, 3)?.toUpperCase() ?? null;
  if (!planCode || (chefIdentityId && !UUID.test(chefIdentityId)) || !name || !billingPeriod || !PERIODS.has(billingPeriod) || !Number.isFinite(amount) || amount < 0 || !currency || !/^[A-Z]{3}$/.test(currency)) return null;
  return { planCode, chefIdentityId, name, description: optionalText(raw.description, 2000), billingPeriod: billingPeriod as AdminPlanPeriod, amount, currency };
}

export function parseAdminPlanStatus(value: unknown): { status: AdminPlanStatus } | null {
  if (!value || typeof value !== "object") return null;
  const status = text((value as Record<string, unknown>).status, 40);
  return status && STATUSES.has(status) ? { status: status as AdminPlanStatus } : null;
}
