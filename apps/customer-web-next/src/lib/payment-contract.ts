export type PaymentStatus = "CREATED" | "PAYMENT_PENDING" | "PAID" | "FAILED" | "CANCELLED";

export type CustomerPaymentSession = {
  paymentOrderId: string;
  checkoutId: string;
  provider: "CASHFREE" | "RAZORPAY";
  providerOrderId: string;
  providerPaymentId: string | null;
  checkoutKeyId: string | null;
  paymentSessionId: string | null;
  amount: number;
  currency: string;
  status: PaymentStatus;
  createdAt: string;
};

export type CustomerPaymentStatus = Omit<CustomerPaymentSession, "paymentSessionId" | "checkoutKeyId"> & {
  updatedAt: string;
};

export type CustomerPaymentVerification = Pick<CustomerPaymentSession, "paymentOrderId" | "status" | "providerPaymentId">;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const STATUSES = new Set<PaymentStatus>(["CREATED", "PAYMENT_PENDING", "PAID", "FAILED", "CANCELLED"]);

function record(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
}
function text(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const result = value.trim();
  return result && result.length <= max ? result : null;
}
function uuid(value: unknown): string | null { const result = text(value, 64); return result && UUID.test(result) ? result : null; }
function money(value: unknown): number | null {
  const result = typeof value === "number" ? value : typeof value === "string" ? Number(value) : NaN;
  return Number.isFinite(result) && result >= 0 && result <= 10_000_000 ? result : null;
}
function instant(value: unknown): string | null { return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null; }
function status(value: unknown): PaymentStatus | null {
  const result = text(value, 30) as PaymentStatus | null;
  return result && STATUSES.has(result) ? result : null;
}

export function parsePaymentSession(value: unknown): CustomerPaymentSession | null {
  const raw = record(value); if (!raw) return null;
  const paymentOrderId = uuid(raw.paymentOrderId); const checkoutId = uuid(raw.checkoutId);
  const provider = text(raw.provider, 20)?.toUpperCase();
  const providerOrderId = text(raw.providerOrderId, 500);
  const providerPaymentId = text(raw.providerPaymentId, 500);
  const checkoutKeyId = text(raw.checkoutKeyId, 500);
  const paymentSessionId = text(raw.paymentSessionId, 5_000); const amount = money(raw.amount);
  const currency = text(raw.currency, 3); const nextStatus = status(raw.status); const createdAt = instant(raw.createdAt);
  const validProvider = provider === "CASHFREE" || provider === "RAZORPAY";
  const checkoutReady = provider === "RAZORPAY"
    ? Boolean(providerOrderId && checkoutKeyId)
    : Boolean(providerOrderId && paymentSessionId);
  return paymentOrderId && checkoutId && validProvider && checkoutReady && providerOrderId
      && amount !== null && currency && nextStatus && createdAt
    ? { paymentOrderId, checkoutId, provider, providerOrderId, providerPaymentId, checkoutKeyId,
        paymentSessionId, amount, currency: currency.toUpperCase(), status: nextStatus, createdAt }
    : null;
}

export function parsePaymentStatus(value: unknown): CustomerPaymentStatus | null {
  const raw = record(value); if (!raw) return null;
  const paymentOrderId = uuid(raw.paymentOrderId); const checkoutId = uuid(raw.checkoutId);
  const provider = text(raw.provider, 20)?.toUpperCase();
  const providerOrderId = text(raw.providerOrderId, 500);
  const providerPaymentId = text(raw.providerPaymentId, 500);
  const amount = money(raw.amount); const currency = text(raw.currency, 3); const nextStatus = status(raw.status);
  const createdAt = instant(raw.createdAt); const updatedAt = instant(raw.updatedAt);
  const validProvider = provider === "CASHFREE" || provider === "RAZORPAY";
  return paymentOrderId && checkoutId && validProvider && providerOrderId && amount !== null && currency && nextStatus && createdAt && updatedAt
    ? { paymentOrderId, checkoutId, provider, providerOrderId, providerPaymentId,
        amount, currency: currency.toUpperCase(), status: nextStatus, createdAt, updatedAt }
    : null;
}

export function parsePaymentVerification(value: unknown): CustomerPaymentVerification | null {
  const raw = record(value); if (!raw) return null;
  const paymentOrderId = uuid(raw.paymentOrderId); const nextStatus = status(raw.status);
  const providerPaymentId = text(raw.providerPaymentId, 500);
  return paymentOrderId && nextStatus ? { paymentOrderId, status: nextStatus, providerPaymentId } : null;
}

export function parsePaymentCreateInput(value: unknown): { checkoutId: string } | null {
  const raw = record(value); const checkoutId = raw ? uuid(raw.checkoutId) : null;
  return checkoutId ? { checkoutId } : null;
}
