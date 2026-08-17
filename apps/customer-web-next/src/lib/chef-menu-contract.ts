export type MenuItemStatus = "DRAFT" | "ACTIVE" | "INACTIVE";
export type FoodType = "VEG" | "NON_VEG" | "EGG";
export type SpiceLevel = "MILD" | "MEDIUM" | "SPICY";

export type ChefMenuImage = {
  id: string;
  publicUrl: string | null;
  sortOrder: number;
  primary: boolean;
  contentType: string;
  fileSizeBytes: number;
};

export type ChefMenuItem = {
  id: string;
  itemName: string;
  description: string | null;
  category: string;
  foodType: FoodType;
  price: number;
  currency: string;
  servesCount: number | null;
  preparationTimeMinutes: number | null;
  spiceLevel: SpiceLevel | null;
  unitPackageWeightGrams: number;
  thermoboxRequired: boolean;
  available: boolean;
  status: MenuItemStatus;
  images: ChefMenuImage[];
  createdAt: string;
  updatedAt: string;
};

export type ChefMenuItemInput = Omit<ChefMenuItem, "id" | "images" | "createdAt" | "updatedAt">;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const STATUSES = new Set<MenuItemStatus>(["DRAFT", "ACTIVE", "INACTIVE"]);
const FOOD_TYPES = new Set<FoodType>(["VEG", "NON_VEG", "EGG"]);
const SPICE_LEVELS = new Set<SpiceLevel>(["MILD", "MEDIUM", "SPICY"]);

function text(value: unknown, max: number): string | null {
  if (typeof value !== "string") return null;
  const result = value.trim();
  return result && result.length <= max ? result : null;
}
function optional(value: unknown, max: number): string | null {
  return value === null || value === undefined || value === "" ? null : text(value, max);
}
function positiveInt(value: unknown, optionalValue = false): number | null {
  if (optionalValue && (value === null || value === undefined || value === "")) return null;
  const number = typeof value === "number" ? value : Number(value);
  return Number.isInteger(number) && number > 0 && number <= 100000 ? number : null;
}
function money(value: unknown): number | null {
  const number = typeof value === "number" ? value : Number(value);
  return Number.isFinite(number) && number >= 0.01 && number <= 10_000_000 ? number : null;
}
function instant(value: unknown): string | null {
  return typeof value === "string" && !Number.isNaN(Date.parse(value)) ? value : null;
}

function parseImage(value: unknown): ChefMenuImage | null {
  if (!value || typeof value !== "object") return null;
  const image = value as Record<string, unknown>;
  const id = text(image.id, 64);
  const publicUrl = optional(image.publicUrl, 2000);
  const contentType = text(image.contentType, 100);
  const fileSizeBytes = typeof image.fileSizeBytes === "number" && Number.isSafeInteger(image.fileSizeBytes) ? image.fileSizeBytes : -1;
  const sortOrder = typeof image.sortOrder === "number" && Number.isInteger(image.sortOrder) ? image.sortOrder : -1;
  if (!id || !UUID.test(id) || !contentType || fileSizeBytes < 0 || fileSizeBytes > 20_000_000 || sortOrder < 0 || (publicUrl && !publicUrl.startsWith("https://"))) return null;
  return { id, publicUrl, contentType, fileSizeBytes, sortOrder, primary: image.primary === true };
}

export function parseChefMenuItem(value: unknown): ChefMenuItem | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const id = text(raw.id, 64);
  const itemName = text(raw.itemName, 180);
  const category = text(raw.category, 80);
  const foodType = text(raw.foodType, 40) as FoodType | null;
  const status = text(raw.status, 40) as MenuItemStatus | null;
  const price = money(raw.price);
  const currency = text(raw.currency, 3);
  const unitPackageWeightGrams = positiveInt(raw.unitPackageWeightGrams);
  const createdAt = instant(raw.createdAt);
  const updatedAt = instant(raw.updatedAt);
  const images = (Array.isArray(raw.images) ? raw.images.slice(0, 20) : []).map(parseImage);
  const spiceLevel = optional(raw.spiceLevel, 40) as SpiceLevel | null;
  if (!id || !UUID.test(id) || !itemName || !category || !foodType || !FOOD_TYPES.has(foodType) || !status || !STATUSES.has(status) || price === null || !currency || unitPackageWeightGrams === null || !createdAt || !updatedAt || images.some(image => image === null) || (spiceLevel && !SPICE_LEVELS.has(spiceLevel))) return null;
  return {
    id, itemName, description: optional(raw.description, 2000), category, foodType, price, currency: currency.toUpperCase(),
    servesCount: positiveInt(raw.servesCount, true), preparationTimeMinutes: positiveInt(raw.preparationTimeMinutes, true), spiceLevel,
    unitPackageWeightGrams, thermoboxRequired: raw.thermoboxRequired === true, available: raw.available === true, status,
    images: images as ChefMenuImage[], createdAt, updatedAt
  };
}

export function parseChefMenuItems(value: unknown): ChefMenuItem[] | null {
  if (!Array.isArray(value) || value.length > 500) return null;
  const items = value.map(parseChefMenuItem);
  return items.some(item => item === null) ? null : items as ChefMenuItem[];
}

export function parseChefMenuItemInput(value: unknown): ChefMenuItemInput | null {
  if (!value || typeof value !== "object") return null;
  const raw = value as Record<string, unknown>;
  const itemName = text(raw.itemName, 180);
  const category = text(raw.category, 80);
  const foodType = text(raw.foodType, 40) as FoodType | null;
  const status = text(raw.status, 40) as MenuItemStatus | null;
  const price = money(raw.price);
  const currency = text(raw.currency, 3);
  const unitPackageWeightGrams = positiveInt(raw.unitPackageWeightGrams);
  const spiceLevel = optional(raw.spiceLevel, 40) as SpiceLevel | null;
  if (!itemName || !category || !foodType || !FOOD_TYPES.has(foodType) || !status || !STATUSES.has(status) || price === null || !currency || unitPackageWeightGrams === null || (spiceLevel && !SPICE_LEVELS.has(spiceLevel))) return null;
  return {
    itemName, description: optional(raw.description, 2000), category, foodType, price, currency: currency.toUpperCase(),
    servesCount: positiveInt(raw.servesCount, true), preparationTimeMinutes: positiveInt(raw.preparationTimeMinutes, true), spiceLevel,
    unitPackageWeightGrams, thermoboxRequired: raw.thermoboxRequired === true, available: raw.available === true, status
  };
}
