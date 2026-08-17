export type PublicMenuItemDetail = {
  id: string;
  kitchenId: string;
  kitchenName: string;
  kitchenDisplayName: string | null;
  areaName: string | null;
  city: string | null;
  state: string | null;
  itemName: string;
  description: string | null;
  category: string;
  foodType: "VEG" | "NON_VEG" | "EGG";
  price: number;
  currency: string;
  servesCount: number | null;
  preparationTimeMinutes: number | null;
  spiceLevel: "MILD" | "MEDIUM" | "SPICY" | null;
  primaryImageUrl: string | null;
};

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const FOOD_TYPES = new Set(["VEG", "NON_VEG", "EGG"]);
const SPICE_LEVELS = new Set(["MILD", "MEDIUM", "SPICY"]);

function object(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function text(value: unknown, maxLength: number): string | null {
  if (typeof value !== "string") return null;
  const result = value.trim();
  return result && result.length <= maxLength ? result : null;
}

function number(value: unknown, min: number, max: number): number | null {
  const result =
    typeof value === "number"
      ? value
      : typeof value === "string"
        ? Number(value)
        : Number.NaN;
  return Number.isFinite(result) && result >= min && result <= max
    ? result
    : null;
}

function integer(value: unknown, min: number, max: number): number | null {
  const result = number(value, min, max);
  return result !== null && Number.isInteger(result) ? result : null;
}

function httpsUrl(value: unknown): string | null {
  const candidate = text(value, 2_000);
  if (!candidate) return null;
  try {
    const parsed = new URL(candidate);
    return parsed.protocol === "https:" ? parsed.toString() : null;
  } catch {
    return null;
  }
}

function primaryImage(images: unknown): string | null {
  if (!Array.isArray(images) || images.length > 30) return null;
  const parsed = images
    .map((value) => object(value))
    .filter((value): value is Record<string, unknown> => value !== null)
    .map((value) => ({
      url: httpsUrl(value.publicUrl),
      primary: value.primary === true,
      sortOrder: integer(value.sortOrder, 0, 10_000) ?? 10_000,
    }))
    .filter((value) => value.url !== null)
    .sort((left, right) => Number(right.primary) - Number(left.primary) || left.sortOrder - right.sortOrder);
  return parsed[0]?.url ?? null;
}

/**
 * Converts the private upstream catalog DTOs into a public allow-listed shape.
 * Identity, contact and pickup-address fields from the kitchen DTO are ignored.
 */
export function parsePublicMenuItemDetail(
  itemValue: unknown,
  kitchenValue: unknown,
): PublicMenuItemDetail | null {
  const item = object(itemValue);
  const kitchen = object(kitchenValue);
  if (!item || !kitchen) return null;

  const id = text(item.id, 64);
  const kitchenId = text(item.kitchenId, 64);
  const itemName = text(item.itemName, 180);
  const category = text(item.category, 80);
  const foodType = text(item.foodType, 20);
  const price = number(item.price, 0.01, 10_000_000);
  const currency = text(item.currency, 3);
  const kitchenName = text(kitchen.kitchenName, 180);
  const spice = text(item.spiceLevel, 20);

  if (
    !id ||
    !UUID.test(id) ||
    !kitchenId ||
    !UUID.test(kitchenId) ||
    !itemName ||
    !category ||
    !foodType ||
    !FOOD_TYPES.has(foodType) ||
    price === null ||
    !currency ||
    !kitchenName ||
    kitchen.id !== kitchenId ||
    item.available !== true ||
    item.status !== "ACTIVE"
  ) {
    return null;
  }

  return {
    id,
    kitchenId,
    kitchenName,
    kitchenDisplayName: text(kitchen.displayName, 180),
    areaName: text(kitchen.areaName, 120),
    city: text(kitchen.city, 120),
    state: text(kitchen.state, 120),
    itemName,
    description: text(item.description, 1_000),
    category,
    foodType: foodType as PublicMenuItemDetail["foodType"],
    price,
    currency: currency.toUpperCase(),
    servesCount: integer(item.servesCount, 1, 100),
    preparationTimeMinutes: integer(item.preparationTimeMinutes, 1, 1_440),
    spiceLevel:
      spice && SPICE_LEVELS.has(spice)
        ? (spice as PublicMenuItemDetail["spiceLevel"])
        : null,
    primaryImageUrl: primaryImage(item.images),
  };
}

export function isUuid(value: string): boolean {
  return UUID.test(value);
}
