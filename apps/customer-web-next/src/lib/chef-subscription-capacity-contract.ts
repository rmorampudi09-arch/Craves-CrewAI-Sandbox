export type ChefCapacitySlotRule = {
  id: string;
  chefIdentityId: string;
  isoDayOfWeek: number;
  mealSlotCode: string;
  totalCapacityUnits: number;
  subscriptionCapacityUnits: number;
  salesEnabled: boolean;
  recurringReservedUnits: number;
  recurringAvailableUnits: number;
  recurringDeficitUnits: number;
  version: number;
  updatedAt: string;
};

export type ChefCapacityMenuRule = {
  id: string;
  chefIdentityId: string;
  menuItemId: string;
  isoDayOfWeek: number;
  mealSlotCode: string;
  maxSubscriptionUnits: number;
  salesEnabled: boolean;
  recurringReservedUnits: number;
  recurringAvailableUnits: number;
  recurringDeficitUnits: number;
  version: number;
  updatedAt: string;
};

export type ChefCapacityDateOverride = {
  id: string;
  chefIdentityId: string;
  serviceDate: string;
  mealSlotCode: string;
  totalCapacityUnits: number;
  subscriptionCapacityUnits: number;
  closed: boolean;
  reason: string | null;
  heldUnits: number;
  committedUnits: number;
  deficitUnits: number;
  updatedAt: string;
};

export type ChefCapacityMenuDateOverride = {
  id: string;
  chefIdentityId: string;
  menuItemId: string;
  serviceDate: string;
  mealSlotCode: string;
  maxSubscriptionUnits: number;
  closed: boolean;
  reason: string | null;
  heldUnits: number;
  committedUnits: number;
  deficitUnits: number;
  updatedAt: string;
};

export type ChefCapacitySummary = {
  chefIdentityId: string;
  adminSalesFrozen: boolean;
  freezeReason: string | null;
  slotRules: ChefCapacitySlotRule[];
  menuItemRules: ChefCapacityMenuRule[];
  dateOverrides: ChefCapacityDateOverride[];
  menuItemDateOverrides: ChefCapacityMenuDateOverride[];
  openIncidentCount: number;
};

export type SlotRuleInput = {
  isoDayOfWeek: number;
  mealSlotCode: string;
  totalCapacityUnits: number;
  subscriptionCapacityUnits: number;
  salesEnabled: boolean;
  reason: string;
};

export type MenuRuleInput = {
  menuItemId: string;
  isoDayOfWeek: number;
  mealSlotCode: string;
  maxSubscriptionUnits: number;
  salesEnabled: boolean;
  reason: string;
};

export type DateOverrideInput = {
  serviceDate: string;
  mealSlotCode: string;
  totalCapacityUnits: number;
  subscriptionCapacityUnits: number;
  closed: boolean;
  reason: string;
};

export type MenuDateOverrideInput = {
  menuItemId: string;
  serviceDate: string;
  mealSlotCode: string;
  maxSubscriptionUnits: number;
  closed: boolean;
  reason: string;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SLOT = /^[A-Z0-9][A-Z0-9_-]{0,39}$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

function object(value: unknown): Record<string, unknown> | null {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function uuid(value: unknown): string | null {
  return typeof value === "string" && UUID.test(value) ? value : null;
}

function slot(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.trim().toUpperCase();
  return SLOT.test(normalized) ? normalized : null;
}

function date(value: unknown): string | null {
  return typeof value === "string" && DATE.test(value) ? value : null;
}

function instant(value: unknown): string | null {
  return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null;
}

function integer(value: unknown, minimum = 0, maximum = 100000): number | null {
  return Number.isInteger(value) && Number(value) >= minimum && Number(value) <= maximum
    ? Number(value)
    : null;
}

function optionalText(value: unknown, maximum = 1000): string | null | undefined {
  if (value === null || value === undefined || value === "") return null;
  if (typeof value !== "string") return undefined;
  const normalized = value.trim();
  return normalized && normalized.length <= maximum ? normalized : undefined;
}

function parseSlotRule(value: unknown): ChefCapacitySlotRule | null {
  const raw = object(value);
  if (!raw) return null;
  const id = uuid(raw.id);
  const chefIdentityId = uuid(raw.chefIdentityId);
  const day = integer(raw.isoDayOfWeek, 1, 7);
  const mealSlotCode = slot(raw.mealSlotCode);
  const totalCapacityUnits = integer(raw.totalCapacityUnits);
  const subscriptionCapacityUnits = integer(raw.subscriptionCapacityUnits);
  const recurringReservedUnits = integer(raw.recurringReservedUnits);
  const recurringAvailableUnits = integer(raw.recurringAvailableUnits);
  const recurringDeficitUnits = integer(raw.recurringDeficitUnits);
  const version = integer(raw.version, 1, Number.MAX_SAFE_INTEGER);
  const updatedAt = instant(raw.updatedAt);
  if (!id || !chefIdentityId || day === null || !mealSlotCode || totalCapacityUnits === null ||
      subscriptionCapacityUnits === null || subscriptionCapacityUnits > totalCapacityUnits ||
      typeof raw.salesEnabled !== "boolean" || recurringReservedUnits === null || recurringAvailableUnits === null ||
      recurringDeficitUnits === null || version === null || !updatedAt) return null;
  return {
    id, chefIdentityId, isoDayOfWeek: day, mealSlotCode, totalCapacityUnits,
    subscriptionCapacityUnits, salesEnabled: raw.salesEnabled, recurringReservedUnits,
    recurringAvailableUnits, recurringDeficitUnits, version, updatedAt,
  };
}

function parseMenuRule(value: unknown): ChefCapacityMenuRule | null {
  const raw = object(value);
  if (!raw) return null;
  const id = uuid(raw.id), chefIdentityId = uuid(raw.chefIdentityId), menuItemId = uuid(raw.menuItemId);
  const day = integer(raw.isoDayOfWeek, 1, 7), mealSlotCode = slot(raw.mealSlotCode);
  const maxSubscriptionUnits = integer(raw.maxSubscriptionUnits);
  const recurringReservedUnits = integer(raw.recurringReservedUnits);
  const recurringAvailableUnits = integer(raw.recurringAvailableUnits);
  const recurringDeficitUnits = integer(raw.recurringDeficitUnits);
  const version = integer(raw.version, 1, Number.MAX_SAFE_INTEGER), updatedAt = instant(raw.updatedAt);
  if (!id || !chefIdentityId || !menuItemId || day === null || !mealSlotCode || maxSubscriptionUnits === null ||
      typeof raw.salesEnabled !== "boolean" || recurringReservedUnits === null || recurringAvailableUnits === null ||
      recurringDeficitUnits === null || version === null || !updatedAt) return null;
  return { id, chefIdentityId, menuItemId, isoDayOfWeek: day, mealSlotCode, maxSubscriptionUnits,
    salesEnabled: raw.salesEnabled, recurringReservedUnits, recurringAvailableUnits, recurringDeficitUnits,
    version, updatedAt };
}

function parseDateOverride(value: unknown): ChefCapacityDateOverride | null {
  const raw = object(value);
  if (!raw) return null;
  const id = uuid(raw.id), chefIdentityId = uuid(raw.chefIdentityId), serviceDate = date(raw.serviceDate);
  const mealSlotCode = slot(raw.mealSlotCode), totalCapacityUnits = integer(raw.totalCapacityUnits);
  const subscriptionCapacityUnits = integer(raw.subscriptionCapacityUnits), heldUnits = integer(raw.heldUnits);
  const committedUnits = integer(raw.committedUnits), deficitUnits = integer(raw.deficitUnits);
  const reason = optionalText(raw.reason), updatedAt = instant(raw.updatedAt);
  if (!id || !chefIdentityId || !serviceDate || !mealSlotCode || totalCapacityUnits === null ||
      subscriptionCapacityUnits === null || subscriptionCapacityUnits > totalCapacityUnits || typeof raw.closed !== "boolean" ||
      reason === undefined || heldUnits === null || committedUnits === null || deficitUnits === null || !updatedAt) return null;
  return { id, chefIdentityId, serviceDate, mealSlotCode, totalCapacityUnits, subscriptionCapacityUnits,
    closed: raw.closed, reason, heldUnits, committedUnits, deficitUnits, updatedAt };
}

function parseMenuDateOverride(value: unknown): ChefCapacityMenuDateOverride | null {
  const raw = object(value);
  if (!raw) return null;
  const id = uuid(raw.id), chefIdentityId = uuid(raw.chefIdentityId), menuItemId = uuid(raw.menuItemId);
  const serviceDate = date(raw.serviceDate), mealSlotCode = slot(raw.mealSlotCode);
  const maxSubscriptionUnits = integer(raw.maxSubscriptionUnits), heldUnits = integer(raw.heldUnits);
  const committedUnits = integer(raw.committedUnits), deficitUnits = integer(raw.deficitUnits);
  const reason = optionalText(raw.reason), updatedAt = instant(raw.updatedAt);
  if (!id || !chefIdentityId || !menuItemId || !serviceDate || !mealSlotCode || maxSubscriptionUnits === null ||
      typeof raw.closed !== "boolean" || reason === undefined || heldUnits === null || committedUnits === null ||
      deficitUnits === null || !updatedAt) return null;
  return { id, chefIdentityId, menuItemId, serviceDate, mealSlotCode, maxSubscriptionUnits,
    closed: raw.closed, reason, heldUnits, committedUnits, deficitUnits, updatedAt };
}

export function parseChefCapacitySummary(value: unknown): ChefCapacitySummary | null {
  const raw = object(value);
  if (!raw) return null;
  const chefIdentityId = uuid(raw.chefIdentityId);
  const freezeReason = optionalText(raw.freezeReason);
  const incidentCount = integer(raw.openIncidentCount, 0, Number.MAX_SAFE_INTEGER);
  if (!chefIdentityId || typeof raw.adminSalesFrozen !== "boolean" || freezeReason === undefined ||
      incidentCount === null || !Array.isArray(raw.slotRules) || !Array.isArray(raw.menuItemRules) ||
      !Array.isArray(raw.dateOverrides) || !Array.isArray(raw.menuItemDateOverrides)) return null;
  const slotRules = raw.slotRules.map(parseSlotRule), menuItemRules = raw.menuItemRules.map(parseMenuRule);
  const dateOverrides = raw.dateOverrides.map(parseDateOverride), menuItemDateOverrides = raw.menuItemDateOverrides.map(parseMenuDateOverride);
  if ([...slotRules, ...menuItemRules, ...dateOverrides, ...menuItemDateOverrides].some(item => item === null)) return null;
  return {
    chefIdentityId, adminSalesFrozen: raw.adminSalesFrozen, freezeReason,
    slotRules: slotRules as ChefCapacitySlotRule[], menuItemRules: menuItemRules as ChefCapacityMenuRule[],
    dateOverrides: dateOverrides as ChefCapacityDateOverride[],
    menuItemDateOverrides: menuItemDateOverrides as ChefCapacityMenuDateOverride[], openIncidentCount: incidentCount,
  };
}

function requiredReason(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 && value.trim().length <= 1000 ? value.trim() : null;
}

export function parseSlotRuleInput(value: unknown): SlotRuleInput | null {
  const raw = object(value); if (!raw) return null;
  const isoDayOfWeek = integer(raw.isoDayOfWeek, 1, 7), mealSlotCode = slot(raw.mealSlotCode);
  const totalCapacityUnits = integer(raw.totalCapacityUnits), subscriptionCapacityUnits = integer(raw.subscriptionCapacityUnits);
  const reason = requiredReason(raw.reason);
  if (isoDayOfWeek === null || !mealSlotCode || totalCapacityUnits === null || subscriptionCapacityUnits === null ||
      subscriptionCapacityUnits > totalCapacityUnits || typeof raw.salesEnabled !== "boolean" || !reason) return null;
  return { isoDayOfWeek, mealSlotCode, totalCapacityUnits, subscriptionCapacityUnits, salesEnabled: raw.salesEnabled, reason };
}

export function parseMenuRuleInput(value: unknown): MenuRuleInput | null {
  const raw = object(value); if (!raw) return null;
  const menuItemId = uuid(raw.menuItemId), isoDayOfWeek = integer(raw.isoDayOfWeek, 1, 7);
  const mealSlotCode = slot(raw.mealSlotCode), maxSubscriptionUnits = integer(raw.maxSubscriptionUnits), reason = requiredReason(raw.reason);
  if (!menuItemId || isoDayOfWeek === null || !mealSlotCode || maxSubscriptionUnits === null || typeof raw.salesEnabled !== "boolean" || !reason) return null;
  return { menuItemId, isoDayOfWeek, mealSlotCode, maxSubscriptionUnits, salesEnabled: raw.salesEnabled, reason };
}

export function parseDateOverrideInput(value: unknown): DateOverrideInput | null {
  const raw = object(value); if (!raw) return null;
  const serviceDate = date(raw.serviceDate), mealSlotCode = slot(raw.mealSlotCode);
  const totalCapacityUnits = integer(raw.totalCapacityUnits), subscriptionCapacityUnits = integer(raw.subscriptionCapacityUnits), reason = requiredReason(raw.reason);
  if (!serviceDate || !mealSlotCode || totalCapacityUnits === null || subscriptionCapacityUnits === null ||
      subscriptionCapacityUnits > totalCapacityUnits || typeof raw.closed !== "boolean" || !reason) return null;
  return { serviceDate, mealSlotCode, totalCapacityUnits, subscriptionCapacityUnits, closed: raw.closed, reason };
}

export function parseMenuDateOverrideInput(value: unknown): MenuDateOverrideInput | null {
  const raw = object(value); if (!raw) return null;
  const menuItemId = uuid(raw.menuItemId), serviceDate = date(raw.serviceDate), mealSlotCode = slot(raw.mealSlotCode);
  const maxSubscriptionUnits = integer(raw.maxSubscriptionUnits), reason = requiredReason(raw.reason);
  if (!menuItemId || !serviceDate || !mealSlotCode || maxSubscriptionUnits === null || typeof raw.closed !== "boolean" || !reason) return null;
  return { menuItemId, serviceDate, mealSlotCode, maxSubscriptionUnits, closed: raw.closed, reason };
}
