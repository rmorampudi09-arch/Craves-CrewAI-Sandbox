export type PageMetadata = { page: number; size: number; totalElements: number; totalPages: number; hasNext: boolean };
export type NearbyKitchen = { id: string; kitchenName: string; displayName: string | null; description: string | null; areaName: string | null; city: string; state: string; distanceMeters: number; activeMenuItemCount: number };
export type NearbyMenuItem = { id: string; kitchenId: string; kitchenName: string; kitchenDisplayName: string | null; areaName: string | null; city: string; state: string; distanceMeters: number; itemName: string; description: string | null; category: string; foodType: "VEG" | "NON_VEG" | "EGG"; price: number; currency: string; servesCount: number | null; preparationTimeMinutes: number | null; spiceLevel: "MILD" | "MEDIUM" | "SPICY" | null; primaryImageUrl: string | null };
export type NearbyKitchenDiscovery = { latitude: number; longitude: number; radiusMeters: number; page: PageMetadata; kitchens: NearbyKitchen[] };
export type NearbyMenuDiscovery = { latitude: number; longitude: number; radiusMeters: number; page: PageMetadata; menuItems: NearbyMenuItem[] };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const FOOD_TYPES = new Set(["VEG", "NON_VEG", "EGG"]);
const SPICE_LEVELS = new Set(["MILD", "MEDIUM", "SPICY"]);

function object(value: unknown): Record<string, unknown> | null { return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null; }
function text(value: unknown, max = 300): string | null { if (typeof value !== "string") return null; const result = value.trim(); return result && result.length <= max ? result : null; }
function number(value: unknown, min: number, max: number): number | null { const result = typeof value === "number" ? value : typeof value === "string" ? Number(value) : NaN; return Number.isFinite(result) && result >= min && result <= max ? result : null; }
function integer(value: unknown, min: number, max: number): number | null { const result = number(value, min, max); return result !== null && Number.isInteger(result) ? result : null; }
function httpsUrl(value: unknown): string | null { const candidate = text(value, 2_000); if (!candidate) return null; try { const url = new URL(candidate); return url.protocol === "https:" ? url.toString() : null; } catch { return null; } }

function parsePage(value: unknown): PageMetadata | null {
  const raw = object(value); if (!raw) return null;
  const page = integer(raw.page, 0, 1_000_000); const size = integer(raw.size, 1, 100); const totalElements = integer(raw.totalElements, 0, Number.MAX_SAFE_INTEGER); const totalPages = integer(raw.totalPages, 0, Number.MAX_SAFE_INTEGER);
  return page !== null && size !== null && totalElements !== null && totalPages !== null && typeof raw.hasNext === "boolean" ? { page, size, totalElements, totalPages, hasNext: raw.hasNext } : null;
}

function parseKitchen(value: unknown): NearbyKitchen | null {
  const raw = object(value); if (!raw) return null;
  const id = text(raw.id, 64); const kitchenName = text(raw.kitchenName, 180); const city = text(raw.city, 120); const state = text(raw.state, 120); const distanceMeters = integer(raw.distanceMeters, 0, 10_000_000); const activeMenuItemCount = integer(raw.activeMenuItemCount, 0, 100_000);
  if (!id || !UUID.test(id) || !kitchenName || !city || !state || distanceMeters === null || activeMenuItemCount === null) return null;
  return { id, kitchenName, displayName: text(raw.displayName, 180), description: text(raw.description, 1_000), areaName: text(raw.areaName, 120), city, state, distanceMeters, activeMenuItemCount };
}

function parseMenuItem(value: unknown): NearbyMenuItem | null {
  const raw = object(value); if (!raw) return null;
  const id = text(raw.id, 64); const kitchenId = text(raw.kitchenId, 64); const kitchenName = text(raw.kitchenName, 180); const itemName = text(raw.itemName, 180); const category = text(raw.category, 80); const foodType = text(raw.foodType, 20); const city = text(raw.city, 120); const state = text(raw.state, 120); const currency = text(raw.currency, 3); const price = number(raw.price, 0, 10_000_000); const distanceMeters = integer(raw.distanceMeters, 0, 10_000_000);
  if (!id || !UUID.test(id) || !kitchenId || !UUID.test(kitchenId) || !kitchenName || !itemName || !category || !foodType || !FOOD_TYPES.has(foodType) || !city || !state || !currency || price === null || distanceMeters === null) return null;
  const spice = text(raw.spiceLevel, 20);
  return { id, kitchenId, kitchenName, kitchenDisplayName: text(raw.kitchenDisplayName, 180), areaName: text(raw.areaName, 120), city, state, distanceMeters, itemName, description: text(raw.description, 1_000), category, foodType: foodType as NearbyMenuItem["foodType"], price, currency: currency.toUpperCase(), servesCount: integer(raw.servesCount, 1, 100), preparationTimeMinutes: integer(raw.preparationTimeMinutes, 1, 1_440), spiceLevel: spice && SPICE_LEVELS.has(spice) ? spice as NearbyMenuItem["spiceLevel"] : null, primaryImageUrl: httpsUrl(raw.primaryImageUrl) };
}

export function parseKitchenDiscovery(value: unknown): NearbyKitchenDiscovery | null {
  const raw = object(value); if (!raw || !Array.isArray(raw.kitchens) || raw.kitchens.length > 100) return null;
  const latitude = number(raw.latitude, -90, 90); const longitude = number(raw.longitude, -180, 180); const radiusMeters = integer(raw.radiusMeters, 1, 100_000); const page = parsePage(raw.page); const kitchens = raw.kitchens.map(parseKitchen);
  return latitude !== null && longitude !== null && radiusMeters !== null && page && !kitchens.some((item) => item === null) ? { latitude, longitude, radiusMeters, page, kitchens: kitchens as NearbyKitchen[] } : null;
}

export function parseMenuDiscovery(value: unknown): NearbyMenuDiscovery | null {
  const raw = object(value); if (!raw || !Array.isArray(raw.menuItems) || raw.menuItems.length > 100) return null;
  const latitude = number(raw.latitude, -90, 90); const longitude = number(raw.longitude, -180, 180); const radiusMeters = integer(raw.radiusMeters, 1, 100_000); const page = parsePage(raw.page); const menuItems = raw.menuItems.map(parseMenuItem);
  return latitude !== null && longitude !== null && radiusMeters !== null && page && !menuItems.some((item) => item === null) ? { latitude, longitude, radiusMeters, page, menuItems: menuItems as NearbyMenuItem[] } : null;
}

export function formatDistance(distanceMeters: number): string { return distanceMeters < 1_000 ? `${distanceMeters} m` : `${(distanceMeters / 1_000).toFixed(1)} km`; }
