import {
  parseMenuDiscovery,
  type NearbyMenuItem,
} from "@/lib/discovery-contract";
import type { PublicMenuItemDetail } from "@/lib/public-menu-item-contract";
import { candidateDiscoveryRadii } from "@/lib/catalog-discovery-policy";

export type Dish = {
  id: string;
  name: string;
  chef: string;
  category: string;
  img: string;
  imageIsPlaceholder?: boolean;
  price: number;
  rating: number;
  time: string;
  veg: boolean;
  tag?: string;
  desc: string;
  ingredients?: string[];
  serves?: string;
  originalPrice?: number;
  spiceLevel?: "Mild" | "Medium" | "Hot";
  reviewCount?: number;
  reviews?: { name: string; rating: number; daysAgo: number; text: string }[];
  kitchenId?: string;
  currency?: string;
  distanceMeters?: number;
  areaName?: string;
  city?: string;
  state?: string;
};

const PLACEHOLDER_IMAGE = "/brand/craves-logo.svg";
let discoveredDishes: Dish[] = [];
let discoveryRadiusMeters = 5_000;

function spiceLabel(
  value: NearbyMenuItem["spiceLevel"] | PublicMenuItemDetail["spiceLevel"],
): Dish["spiceLevel"] {
  if (value === "SPICY") return "Hot";
  if (value === "MEDIUM") return "Medium";
  if (value === "MILD") return "Mild";
  return undefined;
}

function servesLabel(value: number | null): string | undefined {
  return value
    ? `${value} ${value === 1 ? "person" : "people"}`
    : undefined;
}

function mapNearbyItem(item: NearbyMenuItem): Dish {
  const image = item.primaryImageUrl || PLACEHOLDER_IMAGE;
  return {
    id: item.id,
    kitchenId: item.kitchenId,
    name: item.itemName,
    chef: item.kitchenDisplayName || item.kitchenName,
    category: item.category,
    img: image,
    imageIsPlaceholder: !item.primaryImageUrl,
    price: item.price,
    currency: item.currency,
    rating: 0,
    time: item.preparationTimeMinutes
      ? `${item.preparationTimeMinutes} min`
      : "Prepared after ordering",
    veg: item.foodType === "VEG",
    desc: item.description || "Description has not been provided by this kitchen.",
    serves: servesLabel(item.servesCount),
    spiceLevel: spiceLabel(item.spiceLevel),
    distanceMeters: item.distanceMeters,
    areaName: item.areaName ?? undefined,
    city: item.city,
    state: item.state,
  };
}

function isPublicDetail(value: unknown): value is PublicMenuItemDetail {
  if (!value || typeof value !== "object") return false;
  const raw = value as Record<string, unknown>;
  return (
    typeof raw.id === "string" &&
    typeof raw.kitchenId === "string" &&
    typeof raw.kitchenName === "string" &&
    typeof raw.itemName === "string" &&
    typeof raw.category === "string" &&
    typeof raw.foodType === "string" &&
    typeof raw.price === "number" &&
    Number.isFinite(raw.price) &&
    typeof raw.currency === "string"
  );
}

function mapDetail(item: PublicMenuItemDetail): Dish {
  const image = item.primaryImageUrl || PLACEHOLDER_IMAGE;
  return {
    id: item.id,
    kitchenId: item.kitchenId,
    name: item.itemName,
    chef: item.kitchenDisplayName || item.kitchenName,
    category: item.category,
    img: image,
    imageIsPlaceholder: !item.primaryImageUrl,
    price: item.price,
    currency: item.currency,
    rating: 0,
    time: item.preparationTimeMinutes
      ? `${item.preparationTimeMinutes} min`
      : "Prepared after ordering",
    veg: item.foodType === "VEG",
    desc: item.description || "Description has not been provided by this kitchen.",
    serves: servesLabel(item.servesCount),
    spiceLevel: spiceLabel(item.spiceLevel),
    areaName: item.areaName ?? undefined,
    city: item.city ?? undefined,
    state: item.state ?? undefined,
  };
}

function remember(dish: Dish): Dish {
  discoveredDishes = [
    dish,
    ...discoveredDishes.filter((existing) => existing.id !== dish.id),
  ];
  return dish;
}

export async function discoverDishes(
  latitude: number,
  longitude: number,
  radiusMeters = 5_000,
): Promise<Dish[]> {
  for (const candidateRadius of candidateDiscoveryRadii(radiusMeters)) {
    const query = new URLSearchParams({
      latitude: String(latitude),
      longitude: String(longitude),
      radiusMeters: String(candidateRadius),
      page: "0",
      size: "50",
    });
    const response = await fetch(`/api/discovery/menu-items?${query}`, {
      cache: "no-store",
      credentials: "same-origin",
    });
    const body = await response.json().catch(() => null);
    if (!response.ok) {
      const message =
        body &&
        typeof body === "object" &&
        "message" in body &&
        typeof body.message === "string"
          ? body.message
          : "Nearby dishes are temporarily unavailable.";
      throw new Error(message);
    }
    const payload = parseMenuDiscovery(body);
    if (!payload) throw new Error("Craves returned an invalid discovery response.");
    discoveredDishes = payload.menuItems.map(mapNearbyItem);
    discoveryRadiusMeters = candidateRadius;
    if (discoveredDishes.length > 0) return [...discoveredDishes];
  }
  return [];
}

export async function loadKitchenMenu(kitchenId: string): Promise<Dish[]> {
  const response = await fetch(
    `/api/catalog/kitchens/${encodeURIComponent(kitchenId)}/menu-items`,
    {
      cache: "no-store",
      credentials: "same-origin",
    },
  );
  const body = await response.json().catch(() => null);

  if (!response.ok) {
    const message =
      body &&
      typeof body === "object" &&
      "message" in body &&
      typeof body.message === "string"
        ? body.message
        : "This kitchen's menu is temporarily unavailable.";
    throw new Error(message);
  }

  if (!Array.isArray(body) || body.length > 500 || !body.every(isPublicDetail)) {
    throw new Error("Craves returned an invalid kitchen menu response.");
  }

  discoveredDishes = body.map(mapDetail);
  return [...discoveredDishes];
}

export async function loadDish(id: string): Promise<Dish> {
  const cached = getDish(id);
  if (cached) return cached;

  const response = await fetch(`/api/catalog/menu-items/${encodeURIComponent(id)}`, {
    cache: "no-store",
    credentials: "same-origin",
  });
  const body = await response.json().catch(() => null);
  if (!response.ok) {
    const message =
      body &&
      typeof body === "object" &&
      "message" in body &&
      typeof body.message === "string"
        ? body.message
        : "This dish could not be loaded.";
    throw new Error(message);
  }
  if (!isPublicDetail(body)) throw new Error("Craves returned an invalid dish response.");
  return remember(mapDetail(body));
}

export function getDiscoveryRadiusMeters(): number {
  return discoveryRadiusMeters;
}

export function allDishes(): Dish[] {
  return [...discoveredDishes];
}

export function getDish(id: string): Dish | undefined {
  return discoveredDishes.find((dish) => dish.id === id);
}

export function getSimilarDishes(dish: Dish, limit = 4): Dish[] {
  return discoveredDishes
    .filter((candidate) => candidate.id !== dish.id && candidate.category === dish.category)
    .slice(0, limit);
}
