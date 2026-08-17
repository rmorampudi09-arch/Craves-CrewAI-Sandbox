export type SubscriptionPaymentStatus =
  | "PAYMENT_REQUESTED"
  | "PAYMENT_PENDING"
  | "PAID"
  | "FAILED";

export type SubscriptionPayment = {
  id: string;
  invoiceId: string;
  subscriptionId: string;
  cycleStart: string;
  cycleEnd: string;
  amount: number;
  currency: string;
  status: SubscriptionPaymentStatus;
  provider: "CASHFREE" | "RAZORPAY";
  providerOrderId: string | null;
  providerPaymentId: string | null;
  checkoutKeyId: string | null;
  paymentSessionId: string | null;
  providerStatus: string | null;
  createdAt: string;
  updatedAt: string;
  paidAt: string | null;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const CURRENCY = /^[A-Z]{3}$/;
const STATUSES = new Set<SubscriptionPaymentStatus>([
  "PAYMENT_REQUESTED",
  "PAYMENT_PENDING",
  "PAID",
  "FAILED",
]);

function text(value: unknown, maxLength: number): string | null {
  if (typeof value !== "string") return null;
  const result = value.trim();
  return result && result.length <= maxLength ? result : null;
}

function nullableText(value: unknown, maxLength: number): string | null {
  return value === null || value === undefined || value === "" ? null : text(value, maxLength);
}

function dateOnly(value: unknown): string | null {
  return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : null;
}

function instant(value: unknown): string | null {
  return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null;
}

export function parseSubscriptionPayment(value: unknown): SubscriptionPayment | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const id = text(raw.id, 64);
  const invoiceId = text(raw.invoiceId, 64);
  const subscriptionId = text(raw.subscriptionId, 64);
  const cycleStart = dateOnly(raw.cycleStart);
  const cycleEnd = dateOnly(raw.cycleEnd);
  const amount = typeof raw.amount === "number" ? raw.amount : Number(raw.amount);
  const currency = text(raw.currency, 3)?.toUpperCase() ?? null;
  const status = text(raw.status, 40) as SubscriptionPaymentStatus | null;
  const provider = text(raw.provider, 20)?.toUpperCase();
  const createdAt = instant(raw.createdAt);
  const updatedAt = instant(raw.updatedAt);
  const paidAt = raw.paidAt == null ? null : instant(raw.paidAt);

  if (
    !id || !UUID.test(id) ||
    !invoiceId || !UUID.test(invoiceId) ||
    !subscriptionId || !UUID.test(subscriptionId) ||
    !cycleStart || !cycleEnd || cycleEnd <= cycleStart ||
    !Number.isFinite(amount) || amount <= 0 ||
    !currency || !CURRENCY.test(currency) ||
    !status || !STATUSES.has(status) ||
    (provider !== "CASHFREE" && provider !== "RAZORPAY") ||
    !createdAt || !updatedAt ||
    (raw.paidAt != null && !paidAt)
  ) {
    return null;
  }

  return {
    id,
    invoiceId,
    subscriptionId,
    cycleStart,
    cycleEnd,
    amount,
    currency,
    status,
    provider,
    providerOrderId: nullableText(raw.providerOrderId, 500),
    providerPaymentId: nullableText(raw.providerPaymentId, 500),
    checkoutKeyId: nullableText(raw.checkoutKeyId, 500),
    paymentSessionId: nullableText(raw.paymentSessionId, 4096),
    providerStatus: nullableText(raw.providerStatus, 120),
    createdAt,
    updatedAt,
    paidAt,
  };
}
