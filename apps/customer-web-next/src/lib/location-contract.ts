export type ReverseGeocodedAddress = {
  formattedAddress: string;
  houseNumber: string | null;
  street: string | null;
  area: string | null;
  city: string | null;
  district: string | null;
  state: string | null;
  postalCode: string | null;
  country: string | null;
  confidence: "High" | "Medium" | "Low" | null;
  preciseHouseNumber: boolean;
};

type JsonObject = Record<string, unknown>;

function object(value: unknown): JsonObject | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as JsonObject)
    : null;
}

function text(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed || null;
}

function firstText(values: unknown[]): string | null {
  for (const value of values) {
    const candidate = text(value);
    if (candidate) return candidate;
  }
  return null;
}

export function parseReverseGeocodedAddress(value: unknown): ReverseGeocodedAddress | null {
  const root = object(value);
  if (!root) return null;

  const features = Array.isArray(root.features) ? root.features : [];
  const feature = object(features[0]);
  const properties = feature ? object(feature.properties) : null;
  const address = properties ? object(properties.address) : null;
  if (!properties || !address) return null;

  const formattedAddress = text(address.formattedAddress);
  if (!formattedAddress) return null;

  const adminDistricts = Array.isArray(address.adminDistricts)
    ? address.adminDistricts.map(object).filter((item): item is JsonObject => Boolean(item))
    : [];
  const state = firstText([adminDistricts[0]?.name, adminDistricts[0]?.shortName]);
  const district = firstText([
    adminDistricts[1]?.name,
    adminDistricts[2]?.name,
    adminDistricts[3]?.name,
  ]);
  const countryRegion = object(address.countryRegion);
  const houseNumber = text(address.streetNumber);
  const street = text(address.streetName);
  const area = firstText([address.neighborhood, address.locality, district]);
  const city = firstText([address.locality, district]);
  const confidence = text(properties.confidence);

  return {
    formattedAddress,
    houseNumber,
    street,
    area,
    city,
    district,
    state,
    postalCode: text(address.postalCode),
    country: countryRegion ? text(countryRegion.name) : null,
    confidence:
      confidence === "High" || confidence === "Medium" || confidence === "Low"
        ? confidence
        : null,
    preciseHouseNumber: Boolean(houseNumber),
  };
}

export function bestAddressLine1(address: ReverseGeocodedAddress): string {
  if (address.houseNumber && address.street) return `${address.houseNumber}, ${address.street}`;
  if (address.houseNumber) return address.houseNumber;
  if (address.street) return address.street;
  return address.formattedAddress;
}
