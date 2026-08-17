export type ChefEarningStatus =
  | "DRAFT"
  | "APPROVED"
  | "SETTLEMENT_PENDING"
  | "SETTLED"
  | "REVERSED";

export type ChefEarning = {
  id: string;
  orderId: string;
  orderSource: "ON_DEMAND" | "SUBSCRIPTION";
  currency: string;
  grossAmount: number;
  commissionAmount: number;
  taxWithheldAmount: number;
  adjustmentAmount: number;
  netPayable: number;
  allocationReference: string;
  status: ChefEarningStatus;
  reason: string;
  approvedAt: string | null;
  reversedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const STATUSES = new Set<ChefEarningStatus>([
  "DRAFT",
  "APPROVED",
  "SETTLEMENT_PENDING",
  "SETTLED",
  "REVERSED",
]);
const SOURCES = new Set(["ON_DEMAND", "SUBSCRIPTION"]);

function text(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.trim();
  return normalized && normalized.length <= max ? normalized : null;
}

function money(value: unknown, allowNegative = false): number | null {
  const parsed = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(parsed) || Math.abs(parsed) > 10_000_000) return null;
  if (!allowNegative && parsed < 0) return null;
  return parsed;
}

function instant(value: unknown, nullable = false): string | null {
  if (value == null && nullable) return null;
  return typeof value === "string" && !Number.isNaN(Date.parse(value))
    ? value
    : null;
}

export function parseChefEarning(value: unknown): ChefEarning | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const raw = value as Record<string, unknown>;
  const id = text(raw.id, 64);
  const orderId = text(raw.orderId, 64);
  const orderSource = text(raw.orderSource, 30);
  const currency = text(raw.currency, 3);
  const allocationReference = text(raw.allocationReference, 160);
  const status = text(raw.status, 40) as ChefEarningStatus | null;
  const reason = text(raw.reason, 1_000);
  const grossAmount = money(raw.grossAmount);
  const commissionAmount = money(raw.commissionAmount);
  const taxWithheldAmount = money(raw.taxWithheldAmount);
  const adjustmentAmount = money(raw.adjustmentAmount, true);
  const netPayable = money(raw.netPayable);
  const createdAt = instant(raw.createdAt);
  const updatedAt = instant(raw.updatedAt);
  const approvedAt = instant(raw.approvedAt, true);
  const reversedAt = instant(raw.reversedAt, true);

  if (
    !id ||
    !UUID.test(id) ||
    !orderId ||
    !UUID.test(orderId) ||
    !orderSource ||
    !SOURCES.has(orderSource) ||
    !currency ||
    !allocationReference ||
    !status ||
    !STATUSES.has(status) ||
    !reason ||
    grossAmount === null ||
    commissionAmount === null ||
    taxWithheldAmount === null ||
    adjustmentAmount === null ||
    netPayable === null ||
    !createdAt ||
    !updatedAt
  ) {
    return null;
  }

  const arithmetic =
    Math.round(
      (grossAmount - commissionAmount - taxWithheldAmount + adjustmentAmount) *
        100,
    ) / 100;
  if (Math.abs(arithmetic - netPayable) > 0.001) return null;

  return {
    id,
    orderId,
    orderSource: orderSource as ChefEarning["orderSource"],
    currency: currency.toUpperCase(),
    grossAmount,
    commissionAmount,
    taxWithheldAmount,
    adjustmentAmount,
    netPayable,
    allocationReference,
    status,
    reason,
    approvedAt,
    reversedAt,
    createdAt,
    updatedAt,
  };
}

export function parseChefEarnings(value: unknown): ChefEarning[] | null {
  if (!Array.isArray(value) || value.length > 500) return null;
  const entries = value.map(parseChefEarning);
  return entries.some((entry) => entry === null)
    ? null
    : (entries as ChefEarning[]);
}

export function formatChefEarningStatus(status: ChefEarningStatus): string {
  return status
    .toLocaleLowerCase("en-IN")
    .split("_")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(" ");
}
