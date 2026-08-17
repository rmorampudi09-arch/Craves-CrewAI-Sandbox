import type { ReverseGeocodedAddress } from "@/lib/location-contract";

function optionalText(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function parseSanitizedAddress(value: unknown): ReverseGeocodedAddress | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const raw = value as Record<string, unknown>;
  const formattedAddress = optionalText(raw.formattedAddress);
  if (!formattedAddress || typeof raw.preciseHouseNumber !== "boolean") return null;
  const confidence = optionalText(raw.confidence);
  return {
    formattedAddress,
    houseNumber: optionalText(raw.houseNumber),
    street: optionalText(raw.street),
    area: optionalText(raw.area),
    city: optionalText(raw.city),
    district: optionalText(raw.district),
    state: optionalText(raw.state),
    postalCode: optionalText(raw.postalCode),
    country: optionalText(raw.country),
    confidence:
      confidence === "High" || confidence === "Medium" || confidence === "Low"
        ? confidence
        : null,
    preciseHouseNumber: raw.preciseHouseNumber,
  };
}

export async function reverseGeocodeCurrentLocation(
  latitude: number,
  longitude: number,
): Promise<ReverseGeocodedAddress> {
  const response = await fetch("/api/location/reverse-geocode", {
    method: "POST",
    credentials: "same-origin",
    cache: "no-store",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ latitude, longitude }),
  });
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    const message =
      body && typeof body === "object" && "message" in body && typeof body.message === "string"
        ? body.message
        : "Craves could not identify this address right now.";
    throw new Error(message);
  }
  const parsed = parseSanitizedAddress(body);
  if (!parsed) throw new Error("Craves returned an invalid location response.");
  return parsed;
}
