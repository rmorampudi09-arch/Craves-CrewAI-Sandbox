import { parseCustomerOrder, type CustomerOrder } from "./order-contract.ts";

export type CheckoutAddressSnapshot = {
  sourceAddressId: string;
  recipientName: string;
  contactPhoneNumber: string;
  addressLine1: string;
  addressLine2: string | null;
  landmark: string | null;
  areaName: string;
  city: string;
  state: string;
  postalCode: string;
};

export type CustomerCheckout = {
  id: string;
  status: "PAYMENT_PENDING" | "PAID" | "CANCELLED";
  currency: string;
  foodSubtotal: number;
  platformFee: number;
  taxAmount: number;
  deliveryFee: number;
  grandTotal: number;
  chargePolicyId: string;
  deliveryAddressId: string | null;
  deliveryAddress: CheckoutAddressSnapshot | null;
  orders: CustomerOrder[];
  createdAt: string;
};

const RESOURCE_UUID =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
// PostgreSQL accepts the full UUID text shape. Craves intentionally seeds
// policy identifiers such as 20000000-0000-0000-0000-000000000001, whose
// version/variant nibbles are not RFC-generated resource UUIDs.
const POSTGRES_UUID =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const STATUSES = new Set(["PAYMENT_PENDING", "PAID", "CANCELLED"]);

function record(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function text(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const result = value.trim();
  return result && result.length <= max ? result : null;
}

function money(value: unknown): number | null {
  const result =
    typeof value === "number"
      ? value
      : typeof value === "string"
        ? Number(value)
        : Number.NaN;
  return Number.isFinite(result) && result >= 0 && result <= 10_000_000
    ? result
    : null;
}

function parseAddress(value: unknown): CheckoutAddressSnapshot | null {
  const raw = record(value);
  if (!raw) return null;
  const sourceAddressId = text(raw.sourceAddressId, 64);
  const recipientName = text(raw.recipientName, 160);
  const contactPhoneNumber = text(raw.contactPhoneNumber, 16);
  const addressLine1 = text(raw.addressLine1, 250);
  const areaName = text(raw.areaName, 120);
  const city = text(raw.city, 120);
  const state = text(raw.state, 120);
  const postalCode = text(raw.postalCode, 20);
  return sourceAddressId &&
    RESOURCE_UUID.test(sourceAddressId) &&
    recipientName &&
    contactPhoneNumber &&
    addressLine1 &&
    areaName &&
    city &&
    state &&
    postalCode
    ? {
        sourceAddressId,
        recipientName,
        contactPhoneNumber,
        addressLine1,
        addressLine2: text(raw.addressLine2, 250),
        landmark: text(raw.landmark, 160),
        areaName,
        city,
        state,
        postalCode,
      }
    : null;
}

export function parseCheckout(value: unknown): CustomerCheckout | null {
  const raw = record(value);
  if (!raw || !Array.isArray(raw.orders) || raw.orders.length > 100)
    return null;
  const id = text(raw.id, 64);
  const status = text(raw.status, 30);
  const currency = text(raw.currency, 3);
  const chargePolicyId = text(raw.chargePolicyId, 64);
  const deliveryAddressId =
    raw.deliveryAddressId == null ? null : text(raw.deliveryAddressId, 64);
  const createdAt =
    typeof raw.createdAt === "string" &&
    !Number.isNaN(Date.parse(raw.createdAt))
      ? raw.createdAt
      : null;
  const amounts = [
    raw.foodSubtotal,
    raw.platformFee,
    raw.taxAmount,
    raw.deliveryFee,
    raw.grandTotal,
  ].map(money);
  const orders = raw.orders.map(parseCustomerOrder);
  const deliveryAddress =
    raw.deliveryAddress == null ? null : parseAddress(raw.deliveryAddress);
  if (
    !id ||
    !RESOURCE_UUID.test(id) ||
    !status ||
    !STATUSES.has(status) ||
    !currency ||
    !chargePolicyId ||
    !POSTGRES_UUID.test(chargePolicyId) ||
    (deliveryAddressId && !RESOURCE_UUID.test(deliveryAddressId)) ||
    !createdAt ||
    amounts.some((amount) => amount === null) ||
    orders.some((order) => order === null) ||
    (raw.deliveryAddress != null && !deliveryAddress)
  )
    return null;
  return {
    id,
    status: status as CustomerCheckout["status"],
    currency: currency.toUpperCase(),
    foodSubtotal: amounts[0]!,
    platformFee: amounts[1]!,
    taxAmount: amounts[2]!,
    deliveryFee: amounts[3]!,
    grandTotal: amounts[4]!,
    chargePolicyId,
    deliveryAddressId,
    deliveryAddress,
    orders: orders as CustomerOrder[],
    createdAt,
  };
}

export function parseCheckoutInput(
  value: unknown,
): { deliveryAddressId: string; note: string | null } | null {
  const raw = record(value);
  if (!raw) return null;
  const deliveryAddressId = text(raw.deliveryAddressId, 64);
  if (!deliveryAddressId || !RESOURCE_UUID.test(deliveryAddressId)) return null;
  if (raw.note != null && typeof raw.note !== "string") return null;
  const note =
    typeof raw.note === "string" && raw.note.trim() ? raw.note.trim() : null;
  return note && note.length > 500 ? null : { deliveryAddressId, note };
}
