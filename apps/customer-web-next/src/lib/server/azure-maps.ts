import "server-only";

import {
  parseReverseGeocodedAddress,
  type ReverseGeocodedAddress,
} from "@/lib/location-contract";

const AZURE_MAPS_RESOURCE = "https://atlas.microsoft.com/";
const DEFAULT_ENDPOINT = "https://atlas.microsoft.com";
const API_VERSION = "2026-01-01";

type ManagedIdentityTokenResponse = {
  access_token?: unknown;
  expires_on?: unknown;
};

type CachedToken = {
  accessToken: string;
  expiresAtMs: number;
};

let cachedToken: CachedToken | null = null;

function requiredEnvironment(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} is not configured`);
  return value;
}

function parseExpiry(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value)) return value * 1000;
  if (typeof value === "string") {
    const epoch = Number(value);
    if (Number.isFinite(epoch)) return epoch * 1000;
    const parsed = Date.parse(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return Date.now() + 5 * 60_000;
}

async function managedIdentityToken(): Promise<string> {
  if (cachedToken && cachedToken.expiresAtMs - Date.now() > 60_000) {
    return cachedToken.accessToken;
  }

  const endpoint = requiredEnvironment("IDENTITY_ENDPOINT");
  const identityHeader = requiredEnvironment("IDENTITY_HEADER");
  const url = new URL(endpoint);
  url.searchParams.set("resource", AZURE_MAPS_RESOURCE);
  url.searchParams.set("api-version", "2019-08-01");

  const response = await fetch(url, {
    cache: "no-store",
    headers: { "X-IDENTITY-HEADER": identityHeader },
    signal: AbortSignal.timeout(5_000),
  });
  if (!response.ok) {
    throw new Error(`Managed identity token request failed with HTTP ${response.status}`);
  }

  const body = (await response.json().catch(() => null)) as ManagedIdentityTokenResponse | null;
  const accessToken = typeof body?.access_token === "string" ? body.access_token.trim() : "";
  if (!accessToken) throw new Error("Managed identity token response did not include an access token");

  cachedToken = {
    accessToken,
    expiresAtMs: parseExpiry(body?.expires_on),
  };
  return accessToken;
}

export async function reverseGeocodeWithAzureMaps(
  latitude: number,
  longitude: number,
): Promise<ReverseGeocodedAddress> {
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90) {
    throw new Error("Invalid latitude");
  }
  if (!Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    throw new Error("Invalid longitude");
  }

  const mapsClientId = requiredEnvironment("AZURE_MAPS_CLIENT_ID");
  const endpoint = (process.env.AZURE_MAPS_ENDPOINT?.trim() || DEFAULT_ENDPOINT).replace(/\/$/, "");
  const token = await managedIdentityToken();
  const url = new URL(`${endpoint}/reverseGeocode`);
  url.searchParams.set("api-version", API_VERSION);
  url.searchParams.set("coordinates", `${longitude},${latitude}`);
  url.searchParams.set("view", "IN");

  const response = await fetch(url, {
    cache: "no-store",
    headers: {
      Authorization: `Bearer ${token}`,
      "x-ms-client-id": mapsClientId,
      "Accept-Language": "en-IN",
      Accept: "application/geo+json, application/json",
    },
    signal: AbortSignal.timeout(7_000),
  });

  if (!response.ok) {
    throw new Error(`Azure Maps reverse geocoding failed with HTTP ${response.status}`);
  }

  const parsed = parseReverseGeocodedAddress(await response.json().catch(() => null));
  if (!parsed) throw new Error("Azure Maps returned an unusable reverse geocoding response");
  return parsed;
}
