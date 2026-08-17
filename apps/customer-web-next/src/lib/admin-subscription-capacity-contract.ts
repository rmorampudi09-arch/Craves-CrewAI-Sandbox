import { parseChefCapacitySummary, type ChefCapacitySummary } from "@/lib/chef-subscription-capacity-contract";

export type { ChefCapacitySummary };
export { parseChefCapacitySummary };

export type CapacityIncident = {
  id: string;
  chefIdentityId: string;
  serviceDate: string | null;
  isoDayOfWeek: number | null;
  mealSlotCode: string;
  menuItemId: string | null;
  incidentType: "RECURRING_DEFICIT" | "DATE_DEFICIT" | "ITEM_DEFICIT" | "PROJECTION_FAILURE" | "PAID_CAPACITY_CONFLICT";
  severity: "P1" | "P2" | "P3" | "P4";
  status: "OPEN" | "RESOLVED";
  reservedUnits: number;
  capacityUnits: number;
  reason: string;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
};

export type CapacityIncidentPage = {
  items: CapacityIncident[];
  nextCreatedAt: string | null;
  nextId: string | null;
  hasMore: boolean;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const SLOT = /^[A-Z0-9][A-Z0-9_-]{0,39}$/;
const TYPES = new Set(["RECURRING_DEFICIT", "DATE_DEFICIT", "ITEM_DEFICIT", "PROJECTION_FAILURE", "PAID_CAPACITY_CONFLICT"]);
const SEVERITIES = new Set(["P1", "P2", "P3", "P4"]);
const STATUSES = new Set(["OPEN", "RESOLVED"]);

function object(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
}
function uuid(value: unknown): string | null { return typeof value === "string" && UUID.test(value) ? value : null; }
function instant(value: unknown): string | null { return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null; }
function integer(value: unknown, min = 0): number | null { return Number.isInteger(value) && Number(value) >= min ? Number(value) : null; }

function parseIncident(value: unknown): CapacityIncident | null {
  const raw = object(value); if (!raw) return null;
  const id = uuid(raw.id), chefIdentityId = uuid(raw.chefIdentityId);
  const serviceDate = raw.serviceDate == null ? null : typeof raw.serviceDate === "string" && DATE.test(raw.serviceDate) ? raw.serviceDate : undefined;
  const isoDayOfWeek = raw.isoDayOfWeek == null ? null : integer(raw.isoDayOfWeek, 1);
  const mealSlotCode = typeof raw.mealSlotCode === "string" && SLOT.test(raw.mealSlotCode) ? raw.mealSlotCode : null;
  const menuItemId = raw.menuItemId == null ? null : uuid(raw.menuItemId) ?? undefined;
  const incidentType = typeof raw.incidentType === "string" && TYPES.has(raw.incidentType) ? raw.incidentType : null;
  const severity = typeof raw.severity === "string" && SEVERITIES.has(raw.severity) ? raw.severity : null;
  const status = typeof raw.status === "string" && STATUSES.has(raw.status) ? raw.status : null;
  const reservedUnits = integer(raw.reservedUnits), capacityUnits = integer(raw.capacityUnits);
  const reason = typeof raw.reason === "string" && raw.reason.trim().length > 0 && raw.reason.trim().length <= 1000 ? raw.reason.trim() : null;
  const createdAt = instant(raw.createdAt), updatedAt = instant(raw.updatedAt);
  const resolvedAt = raw.resolvedAt == null ? null : instant(raw.resolvedAt);
  if (!id || !chefIdentityId || serviceDate === undefined || (isoDayOfWeek !== null && (isoDayOfWeek < 1 || isoDayOfWeek > 7)) || !mealSlotCode || menuItemId === undefined || !incidentType || !severity || !status || reservedUnits === null || capacityUnits === null || !reason || !createdAt || !updatedAt || (raw.resolvedAt != null && !resolvedAt)) return null;
  return { id, chefIdentityId, serviceDate, isoDayOfWeek, mealSlotCode, menuItemId,
    incidentType: incidentType as CapacityIncident["incidentType"], severity: severity as CapacityIncident["severity"],
    status: status as CapacityIncident["status"], reservedUnits, capacityUnits, reason, createdAt, updatedAt, resolvedAt };
}

export function parseCapacityIncidentPage(value: unknown): CapacityIncidentPage | null {
  const raw = object(value); if (!raw || !Array.isArray(raw.items) || raw.items.length > 200 || typeof raw.hasMore !== "boolean") return null;
  const items = raw.items.map(parseIncident);
  if (items.some(item => item === null)) return null;
  const nextCreatedAt = raw.nextCreatedAt == null ? null : instant(raw.nextCreatedAt);
  const nextId = raw.nextId == null ? null : uuid(raw.nextId);
  if ((raw.nextCreatedAt != null && !nextCreatedAt) || (raw.nextId != null && !nextId) || (raw.hasMore && (!nextCreatedAt || !nextId))) return null;
  return { items: items as CapacityIncident[], nextCreatedAt, nextId, hasMore: raw.hasMore };
}