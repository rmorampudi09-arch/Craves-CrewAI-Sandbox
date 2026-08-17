export type ChefDocumentType =
  | "APPLICANT_PHOTO"
  | "GOVERNMENT_ID_FRONT"
  | "GOVERNMENT_ID_BACK"
  | "TAX_ID_CARD"
  | "AADHAAR_CARD"
  | "PAN_CARD";
export type ChefApplicationStatus = "NOT_SUBMITTED" | "PENDING" | "APPROVED" | "REJECTED";

export type ChefProofDocument = {
  id: string;
  documentType: ChefDocumentType;
  originalFileName: string;
  contentType: string;
  fileSizeBytes: number;
  status: string;
  createdAt: string;
};

export type ChefApplication = {
  id: string | null;
  email: string | null;
  firstName: string | null;
  lastName: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  landmark: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
  latitude: number | null;
  longitude: number | null;
  status: ChefApplicationStatus;
  rejectionReason: string | null;
  submittedAt: string | null;
  reviewedAt: string | null;
  documents: ChefProofDocument[];
};

export type ChefApplicationInput = {
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
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const STATUSES = new Set<ChefApplicationStatus>(["NOT_SUBMITTED", "PENDING", "APPROVED", "REJECTED"]);
const DOCUMENT_TYPES = new Set<ChefDocumentType>([
  "APPLICANT_PHOTO",
  "GOVERNMENT_ID_FRONT",
  "GOVERNMENT_ID_BACK",
  "TAX_ID_CARD",
  "AADHAAR_CARD",
  "PAN_CARD",
]);

function text(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.trim();
  return normalized && normalized.length <= max ? normalized : null;
}
function optionalText(value: unknown, max: number): string | null {
  return value === null || value === undefined || value === "" ? null : text(value, max);
}
function instant(value: unknown): string | null {
  return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null;
}
function coordinate(value: unknown, min: number, max: number): number | null {
  if (value === null || value === undefined || value === "") return null;
  const number = typeof value === "number" ? value : Number(value);
  return Number.isFinite(number) && number >= min && number <= max ? number : null;
}

export function parseChefProofDocument(value: unknown): ChefProofDocument | null {
  if (!value || typeof value !== "object") return null;
  const document = value as Record<string, unknown>;
  const id = text(document.id, 64);
  const documentTypeText = text(document.documentType, 40);
  const documentType = documentTypeText as ChefDocumentType | null;
  const originalFileName = text(document.originalFileName, 255);
  const contentType = text(document.contentType, 100);
  const status = text(document.status, 40);
  const createdAt = instant(document.createdAt);
  const fileSizeBytes = typeof document.fileSizeBytes === "number" && Number.isSafeInteger(document.fileSizeBytes) ? document.fileSizeBytes : -1;
  if (
    !id ||
    !UUID.test(id) ||
    !documentType ||
    !DOCUMENT_TYPES.has(documentType) ||
    !originalFileName ||
    !contentType ||
    !status ||
    !createdAt ||
    fileSizeBytes < 0 ||
    fileSizeBytes > 10_000_000
  ) return null;
  return { id, documentType, originalFileName, contentType, fileSizeBytes, status, createdAt };
}

export function parseChefApplication(value: unknown): ChefApplication | null {
  if (!value || typeof value !== "object") return null;
  const application = value as Record<string, unknown>;
  const status = text(application.status, 40) as ChefApplicationStatus | null;
  const id = optionalText(application.id, 64);
  if (!status || !STATUSES.has(status) || (id && !UUID.test(id))) return null;
  const documents = (Array.isArray(application.documents) ? application.documents.slice(0, 20) : []).map(parseChefProofDocument);
  if (documents.some(document => document === null)) return null;
  return {
    id,
    email: optionalText(application.email, 320),
    firstName: optionalText(application.firstName, 120),
    lastName: optionalText(application.lastName, 120),
    addressLine1: optionalText(application.addressLine1, 250),
    addressLine2: optionalText(application.addressLine2, 250),
    landmark: optionalText(application.landmark, 160),
    city: optionalText(application.city, 120),
    state: optionalText(application.state, 120),
    postalCode: optionalText(application.postalCode, 20),
    latitude: coordinate(application.latitude, -90, 90),
    longitude: coordinate(application.longitude, -180, 180),
    status,
    rejectionReason: optionalText(application.rejectionReason, 1000),
    submittedAt: instant(application.submittedAt),
    reviewedAt: instant(application.reviewedAt),
    documents: documents as ChefProofDocument[]
  };
}

export function parseChefApplicationInput(value: unknown): ChefApplicationInput | null {
  if (!value || typeof value !== "object") return null;
  const input = value as Record<string, unknown>;
  const email = text(input.email, 320);
  const firstName = text(input.firstName, 120);
  const lastName = text(input.lastName, 120);
  const addressLine1 = text(input.addressLine1, 250);
  const city = text(input.city, 120);
  const state = text(input.state, 120);
  const latitude = coordinate(input.latitude, -90, 90);
  const longitude = coordinate(input.longitude, -180, 180);
  const hasLatitude = input.latitude !== null && input.latitude !== undefined && input.latitude !== "";
  const hasLongitude = input.longitude !== null && input.longitude !== undefined && input.longitude !== "";
  if (!email || !EMAIL.test(email) || !firstName || !lastName || !addressLine1 || !city || !state || hasLatitude !== hasLongitude || (hasLatitude && (latitude === null || longitude === null))) return null;
  return { email, firstName, lastName, addressLine1, addressLine2: optionalText(input.addressLine2, 250), landmark: optionalText(input.landmark, 160), city, state, postalCode: optionalText(input.postalCode, 20), latitude, longitude };
}
