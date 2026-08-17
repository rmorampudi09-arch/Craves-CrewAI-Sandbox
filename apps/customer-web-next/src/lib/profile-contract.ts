export type CustomerProfile = {
  id: string;
  registeredPhoneNumber: string;
  firstName: string;
  lastName: string;
  email: string | null;
  createdAt: string;
  updatedAt: string;
};

export type CustomerProfileInput = { firstName: string; lastName: string; email: string | null };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function text(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const result = value.trim();
  return result && result.length <= max ? result : null;
}

function instant(value: unknown): string | null {
  return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null;
}

export function parseProfileInput(value: unknown): CustomerProfileInput | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const firstName = text(raw.firstName, 100);
  const lastName = text(raw.lastName, 100);
  const email = raw.email == null || raw.email === "" ? null : text(raw.email, 320);
  if (!firstName || !lastName || (raw.email != null && raw.email !== "" && !email)) return null;
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return null;
  return { firstName, lastName, email };
}

export function parseCustomerProfile(value: unknown): CustomerProfile | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const id = text(raw.id, 64);
  const registeredPhoneNumber = text(raw.registeredPhoneNumber, 24);
  const firstName = text(raw.firstName, 100);
  const lastName = text(raw.lastName, 100);
  const email = raw.email == null ? null : text(raw.email, 320);
  const createdAt = instant(raw.createdAt);
  const updatedAt = instant(raw.updatedAt);
  if (!id || !UUID.test(id) || !registeredPhoneNumber || !firstName || !lastName || !createdAt || !updatedAt) return null;
  return { id, registeredPhoneNumber, firstName, lastName, email, createdAt, updatedAt };
}
