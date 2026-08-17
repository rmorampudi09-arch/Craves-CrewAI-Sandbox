export type AdminChefApplicationStatus = "PENDING" | "APPROVED" | "REJECTED";

export type AdminChefDocument = {
  id: string;
  documentType: string;
  originalFileName: string;
  contentType: "application/pdf" | "image/jpeg" | "image/png";
  fileSizeBytes: number;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type AdminChefApplication = {
  id: string;
  phoneNumber: string;
  email: string;
  firstName: string;
  lastName: string;
  addressLine1: string;
  addressLine2: string | null;
  landmark: string | null;
  city: string;
  state: string;
  postalCode: string | null;
  latitude: number | null;
  longitude: number | null;
  status: AdminChefApplicationStatus;
  rejectionReason: string | null;
  submittedAt: string;
  reviewedAt: string | null;
  documents: AdminChefDocument[];
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const STATUSES = new Set(["PENDING", "APPROVED", "REJECTED"]);
const CONTENT_TYPES = new Set(["application/pdf", "image/jpeg", "image/png"]);

function text(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const result = value.trim();
  return result && result.length <= max ? result : null;
}
function optionalText(value: unknown, max: number): string | null { return value == null || value === "" ? null : text(value, max); }
function instant(value: unknown): string | null { return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null; }
function coordinate(value: unknown, min: number, max: number): number | null {
  if (value == null || value === "") return null;
  const result = typeof value === "number" ? value : Number(value);
  return Number.isFinite(result) && result >= min && result <= max ? result : null;
}

function parseDocument(value: unknown): AdminChefDocument | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const id = text(raw.id, 64); const documentType = text(raw.documentType, 40); const originalFileName = text(raw.originalFileName, 255);
  const contentType = text(raw.contentType, 100); const status = text(raw.status, 40); const createdAt = instant(raw.createdAt); const updatedAt = instant(raw.updatedAt);
  const fileSizeBytes = typeof raw.fileSizeBytes === "number" && Number.isSafeInteger(raw.fileSizeBytes) ? raw.fileSizeBytes : -1;
  if (!id || !UUID.test(id) || !documentType || !originalFileName || !contentType || !CONTENT_TYPES.has(contentType) || !status || !createdAt || !updatedAt || fileSizeBytes < 1 || fileSizeBytes > 10_000_000) return null;
  return { id, documentType, originalFileName, contentType: contentType as AdminChefDocument["contentType"], fileSizeBytes, status, createdAt, updatedAt };
}

export function parseAdminChefApplication(value: unknown): AdminChefApplication | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const id = text(raw.id, 64); const phoneNumber = text(raw.phoneNumber, 24); const email = text(raw.email, 320);
  const firstName = text(raw.firstName, 120); const lastName = text(raw.lastName, 120); const addressLine1 = text(raw.addressLine1, 250);
  const city = text(raw.city, 120); const state = text(raw.state, 120); const status = text(raw.status, 40); const submittedAt = instant(raw.submittedAt);
  const documents = (Array.isArray(raw.documents) ? raw.documents.slice(0, 20) : []).map(parseDocument);
  if (!id || !UUID.test(id) || !phoneNumber || !email || !firstName || !lastName || !addressLine1 || !city || !state || !status || !STATUSES.has(status) || !submittedAt || documents.some(item => item === null)) return null;
  return { id, phoneNumber, email, firstName, lastName, addressLine1, addressLine2: optionalText(raw.addressLine2, 250), landmark: optionalText(raw.landmark, 160), city, state, postalCode: optionalText(raw.postalCode, 20), latitude: coordinate(raw.latitude, -90, 90), longitude: coordinate(raw.longitude, -180, 180), status: status as AdminChefApplicationStatus, rejectionReason: optionalText(raw.rejectionReason, 1000), submittedAt, reviewedAt: raw.reviewedAt == null ? null : instant(raw.reviewedAt), documents: documents as AdminChefDocument[] };
}

export function parseAdminChefApplications(value: unknown): AdminChefApplication[] | null {
  if (!Array.isArray(value) || value.length > 1000) return null;
  const parsed = value.map(parseAdminChefApplication);
  return parsed.some(item => item === null) ? null : parsed as AdminChefApplication[];
}

export function parseAdminDecision(value: unknown): { reason: string } | null {
  if (!value || typeof value !== "object") return null;
  const reason = text((value as Record<string, unknown>).reason, 1000);
  return reason ? { reason } : null;
}
