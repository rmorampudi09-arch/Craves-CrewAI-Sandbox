export type AdminDashboardMetrics = {
  ordersCreated24h: number;
  chefAcceptancePending: number;
  preparing: number;
  readyForPickup: number;
  outForDelivery: number;
  refundPending: number;
  refundFailed: number;
  delivered24h: number;
};

export type AdminDashboardSummary = {
  generatedAt: string;
  metrics: AdminDashboardMetrics;
  statusCounts: Array<{ status: string; count: number }>;
  orderTrend: Array<{ date: string; count: number }>;
  recentExceptions: Array<{ orderId: string; kitchenName: string | null; status: string; updatedAt: string }>;
};

// PostgreSQL stores canonical UUID text without requiring RFC version bits 1–5.
// Validate the canonical 8-4-4-4-12 shape while leaving UUID semantics to the owning service.
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const STATUSES = new Set([
  "CHEF_ACCEPTANCE_PENDING", "PREPARING", "READY_FOR_PICKUP", "OUT_FOR_DELIVERY",
  "REFUND_PENDING", "REFUND_FAILED", "CHEF_REJECTED", "CANCELLED"
]);

function record(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
}

function count(value: unknown): number | null {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? value : null;
}

function timestamp(value: unknown): string | null {
  if (typeof value !== "string" || value.length > 40 || Number.isNaN(Date.parse(value))) return null;
  return value;
}

function status(value: unknown): string | null {
  return typeof value === "string" && STATUSES.has(value) ? value : null;
}

export function parseAdminDashboardSummary(value: unknown): AdminDashboardSummary | null {
  const raw = record(value);
  const rawMetrics = record(raw?.metrics);
  if (!raw || !rawMetrics) return null;

  const metricNames: Array<keyof AdminDashboardMetrics> = [
    "ordersCreated24h", "chefAcceptancePending", "preparing", "readyForPickup",
    "outForDelivery", "refundPending", "refundFailed", "delivered24h"
  ];
  const metrics = {} as AdminDashboardMetrics;
  for (const name of metricNames) {
    const parsed = count(rawMetrics[name]);
    if (parsed === null) return null;
    metrics[name] = parsed;
  }

  const generatedAt = timestamp(raw.generatedAt);
  if (!generatedAt || !Array.isArray(raw.statusCounts) || !Array.isArray(raw.orderTrend) || !Array.isArray(raw.recentExceptions)) return null;

  const statusCounts = raw.statusCounts.map(item => {
    const entry = record(item);
    const parsedStatus = status(entry?.status);
    const parsedCount = count(entry?.count);
    return entry && parsedStatus && parsedCount !== null ? { status: parsedStatus, count: parsedCount } : null;
  });
  const orderTrend = raw.orderTrend.map(item => {
    const entry = record(item);
    const date = typeof entry?.date === "string" && DATE.test(entry.date) ? entry.date : null;
    const parsedCount = count(entry?.count);
    return entry && date && parsedCount !== null ? { date, count: parsedCount } : null;
  });
  const recentExceptions = raw.recentExceptions.map(item => {
    const entry = record(item);
    const orderId = typeof entry?.orderId === "string" && UUID.test(entry.orderId) ? entry.orderId : null;
    const parsedStatus = status(entry?.status);
    const updatedAt = timestamp(entry?.updatedAt);
    const kitchenName = entry?.kitchenName === null
      ? null
      : typeof entry?.kitchenName === "string" && entry.kitchenName.trim().length <= 160
        ? entry.kitchenName.trim() || null
        : undefined;
    return entry && orderId && parsedStatus && updatedAt && kitchenName !== undefined
      ? { orderId, kitchenName, status: parsedStatus, updatedAt }
      : null;
  });

  if (statusCounts.some(item => !item) || orderTrend.some(item => !item) || recentExceptions.some(item => !item)) return null;
  if (statusCounts.length > 12 || orderTrend.length > 31 || recentExceptions.length > 10) return null;

  return {
    generatedAt,
    metrics,
    statusCounts: statusCounts as AdminDashboardSummary["statusCounts"],
    orderTrend: orderTrend as AdminDashboardSummary["orderTrend"],
    recentExceptions: recentExceptions as AdminDashboardSummary["recentExceptions"]
  };
}
