export type AddressLabel = "HOME" | "WORK" | "OTHER";

export type CustomerAddress = {
  id: string;
  addressLabel: AddressLabel;
  recipientName: string | null;
  contactPhoneNumber: string;
  addressLine1: string;
  addressLine2: string | null;
  landmark: string | null;
  areaName: string | null;
  districtName: string | null;
  city: string;
  state: string;
  postalCode: string | null;
  latitude: number | null;
  longitude: number | null;
  isDefault: boolean;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type DeliveryReadyAddress = CustomerAddress & {
  recipientName: string;
  areaName: string;
  postalCode: string;
  latitude: number;
  longitude: number;
  active: true;
};

export type CustomerAddressInput = {
  addressLabel: AddressLabel;
  recipientName: string;
  contactPhoneNumber: string;
  addressLine1: string;
  addressLine2: string | null;
  landmark: string | null;
  areaName: string;
  districtName: string;
  city: string;
  state: string;
  postalCode: string;
  latitude: number;
  longitude: number;
  isDefault: boolean;
};

export type LocationRecommendation = {
  locationType: "SAVED_ADDRESS" | "LIVE_GPS";
  latitude: number;
  longitude: number;
  selectedSavedAddress: CustomerAddress | null;
  distanceMeters: number | null;
  matchRadiusMeters: number;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const PHONE = /^\+?[0-9]{10,15}$/;
const LABELS = new Set(["HOME", "WORK", "OTHER"]);
const LOCATION_TYPES = new Set(["SAVED_ADDRESS", "LIVE_GPS"]);

function object(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}

function text(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const result = value.trim();
  return result && result.length <= max ? result : null;
}

function optionalText(value: unknown, max: number): string | null {
  return value == null || value === "" ? null : text(value, max);
}

function coordinate(value: unknown, min: number, max: number): number | null {
  const result = typeof value === "number"
    ? value
    : typeof value === "string" && value.trim() !== ""
      ? Number(value)
      : Number.NaN;
  return Number.isFinite(result) && result >= min && result <= max ? result : null;
}

function legacyCoordinate(
  value: unknown,
  min: number,
  max: number,
): number | null | undefined {
  if (value == null || value === "") return null;
  const result = coordinate(value, min, max);
  return result === null ? undefined : result;
}

function instant(value: unknown): string | null {
  return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null;
}

function responseObject(value: unknown): Record<string, unknown> | null {
  const raw = object(value);
  if (!raw) return null;
  for (const key of ["address", "data"] as const) {
    const nested = object(raw[key]);
    if (nested) return nested;
  }
  return raw;
}

function responseArray(value: unknown): unknown[] | null {
  if (Array.isArray(value)) return value;
  const raw = object(value);
  if (!raw) return null;
  for (const key of ["addresses", "items", "content", "data"] as const) {
    if (Array.isArray(raw[key])) return raw[key] as unknown[];
  }
  return null;
}

export function parseAddressInput(value: unknown): CustomerAddressInput | null {
  const raw = object(value);
  if (!raw) return null;

  const addressLabel = text(raw.addressLabel, 10);
  const recipientName = text(raw.recipientName, 160);
  const contactPhoneNumber = text(raw.contactPhoneNumber, 16);
  const addressLine1 = text(raw.addressLine1, 250);
  const areaName = text(raw.areaName, 120);
  const districtName = text(raw.districtName, 120);
  const city = text(raw.city, 120);
  const state = text(raw.state, 120);
  const postalCode = text(raw.postalCode, 20);
  const latitude = coordinate(raw.latitude, -90, 90);
  const longitude = coordinate(raw.longitude, -180, 180);

  if (
    !addressLabel
    || !LABELS.has(addressLabel)
    || !recipientName
    || !contactPhoneNumber
    || !PHONE.test(contactPhoneNumber)
    || !addressLine1
    || !areaName
    || !districtName
    || !city
    || !state
    || !postalCode
    || latitude === null
    || longitude === null
    || typeof raw.isDefault !== "boolean"
  ) {
    return null;
  }

  return {
    addressLabel: addressLabel as AddressLabel,
    recipientName,
    contactPhoneNumber,
    addressLine1,
    addressLine2: optionalText(raw.addressLine2, 250),
    landmark: optionalText(raw.landmark, 160),
    areaName,
    districtName,
    city,
    state,
    postalCode,
    latitude,
    longitude,
    isDefault: raw.isDefault,
  };
}

/**
 * Parse both current address responses and rows created before the location
 * migrations. Historical rows remain visible so the customer can complete
 * them, but missing delivery-critical fields still prevent checkout.
 */
export function parseCustomerAddress(value: unknown): CustomerAddress | null {
  const raw = responseObject(value);
  if (!raw) return null;

  const id = text(raw.id, 64);
  const addressLabel = text(raw.addressLabel, 10);
  const recipientName = optionalText(raw.recipientName, 160);
  const contactPhoneNumber = text(raw.contactPhoneNumber, 16);
  const addressLine1 = text(raw.addressLine1, 250);
  const addressLine2 = optionalText(raw.addressLine2, 250);
  const landmark = optionalText(raw.landmark, 160);
  const areaName = optionalText(raw.areaName, 120);
  const districtName = optionalText(raw.districtName, 120);
  const city = text(raw.city, 120);
  const state = text(raw.state, 120);
  const postalCode = optionalText(raw.postalCode, 20);
  const latitude = legacyCoordinate(raw.latitude, -90, 90);
  const longitude = legacyCoordinate(raw.longitude, -180, 180);
  const createdAt = instant(raw.createdAt);
  const updatedAt = instant(raw.updatedAt);

  if (
    !id
    || !UUID.test(id)
    || !addressLabel
    || !LABELS.has(addressLabel)
    || !contactPhoneNumber
    || !PHONE.test(contactPhoneNumber)
    || !addressLine1
    || !city
    || !state
    || latitude === undefined
    || longitude === undefined
    || (latitude === null) !== (longitude === null)
    || typeof raw.isDefault !== "boolean"
    || typeof raw.active !== "boolean"
    || !createdAt
    || !updatedAt
  ) {
    return null;
  }

  const deliveryReady = Boolean(
    recipientName
    && areaName
    && postalCode
    && latitude !== null
    && longitude !== null
  );

  return {
    id,
    addressLabel: addressLabel as AddressLabel,
    recipientName,
    contactPhoneNumber,
    addressLine1,
    addressLine2,
    landmark,
    areaName,
    districtName,
    city,
    state,
    postalCode,
    latitude,
    longitude,
    isDefault: raw.isDefault,
    active: raw.active && deliveryReady,
    createdAt,
    updatedAt,
  };
}

export function parseCustomerAddresses(value: unknown): CustomerAddress[] | null {
  const raw = responseArray(value);
  if (!raw || raw.length > 100) return null;
  const addresses = raw.map(parseCustomerAddress);
  return addresses.some((address) => address === null)
    ? null
    : addresses as CustomerAddress[];
}

export function isDeliveryReadyAddress(
  address: CustomerAddress,
): address is DeliveryReadyAddress {
  return address.active
    && Boolean(address.recipientName)
    && Boolean(address.areaName)
    && Boolean(address.postalCode)
    && address.latitude !== null
    && address.longitude !== null;
}

export function parseLocationRecommendation(value: unknown): LocationRecommendation | null {
  const raw = object(value);
  if (!raw) return null;
  const locationType = text(raw.locationType, 20);
  const latitude = coordinate(raw.latitude, -90, 90);
  const longitude = coordinate(raw.longitude, -180, 180);
  const matchRadiusMeters = typeof raw.matchRadiusMeters === "number"
    && Number.isInteger(raw.matchRadiusMeters)
    && raw.matchRadiusMeters >= 1
    && raw.matchRadiusMeters <= 100_000
    ? raw.matchRadiusMeters
    : null;
  const distanceMeters = raw.distanceMeters == null
    ? null
    : typeof raw.distanceMeters === "number"
      && Number.isInteger(raw.distanceMeters)
      && raw.distanceMeters >= 0
      ? raw.distanceMeters
      : null;
  const selectedSavedAddress = raw.selectedSavedAddress == null
    ? null
    : parseCustomerAddress(raw.selectedSavedAddress);

  return locationType
    && LOCATION_TYPES.has(locationType)
    && latitude !== null
    && longitude !== null
    && matchRadiusMeters !== null
    && (raw.selectedSavedAddress == null || selectedSavedAddress)
    && (raw.distanceMeters == null || distanceMeters !== null)
    ? {
        locationType: locationType as LocationRecommendation["locationType"],
        latitude,
        longitude,
        selectedSavedAddress,
        distanceMeters,
        matchRadiusMeters,
      }
    : null;
}
