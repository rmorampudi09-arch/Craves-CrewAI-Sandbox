import { allDishes, type Dish } from "@/services/api/dishes";

export type Chef = {
  id: string;
  name: string;
  verified: boolean;
  rating: number;
  reviewCount: number;
  distanceKm: number;
  location: string;
  experienceYears: number;
  ordersDelivered: number;
  bio: string;
  specialties: string[];
  reviews: { name: string; rating: number; daysAgo: number; text: string }[];
  activeDishCount: number;
  catalogBacked: boolean;
};

export function slugifyChefName(name: string): string {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

function buildLiveChef(name: string, dishes: Dish[]): Chef | undefined {
  const catalogDishes = dishes.filter(
    (dish): dish is Dish & { kitchenId: string } =>
      typeof dish.kitchenId === "string" && dish.kitchenId.length > 0,
  );
  if (catalogDishes.length === 0) return undefined;

  const reference = catalogDishes[0];
  const specialties = Array.from(
    new Set(
      catalogDishes
        .map((dish) => dish.category.trim())
        .filter((category) => category.length > 0),
    ),
  );
  const availableRatings = catalogDishes
    .map((dish) => dish.rating)
    .filter((rating) => Number.isFinite(rating) && rating > 0);
  const rating = availableRatings.length
    ? Math.round(
        (availableRatings.reduce((sum, value) => sum + value, 0) /
          availableRatings.length) *
          10,
      ) / 10
    : 0;
  const reviewCount = catalogDishes.reduce(
    (sum, dish) => sum + (dish.reviewCount ?? 0),
    0,
  );
  const location = [reference.areaName, reference.city, reference.state]
    .filter((value): value is string => Boolean(value?.trim()))
    .join(", ");
  const distanceKm =
    typeof reference.distanceMeters === "number"
      ? Math.round((reference.distanceMeters / 1_000) * 10) / 10
      : 0;

  return {
    id: reference.kitchenId,
    name,
    // The current public catalog DTO does not expose approval evidence.
    verified: false,
    rating,
    reviewCount,
    distanceKm,
    location,
    // These fields remain zero until a reviewed public chef-profile contract
    // exposes them. The UI must hide zero values rather than fabricate them.
    experienceYears: 0,
    ordersDelivered: 0,
    bio: "",
    specialties,
    reviews: catalogDishes.flatMap((dish) => dish.reviews ?? []),
    activeDishCount: catalogDishes.length,
    catalogBacked: true,
  };
}

/**
 * Retained for compatibility with older listing components. Static profiles
 * are intentionally not exported; live discovery is the only data source.
 */
export const CHEFS: Chef[] = [];

export function getChef(id: string): Chef | undefined {
  const dishes = allDishes().filter(
    (dish) => dish.kitchenId === id || slugifyChefName(dish.chef) === id,
  );
  return dishes.length > 0 ? buildLiveChef(dishes[0].chef, dishes) : undefined;
}

export function getChefByName(name: string): Chef | undefined {
  const dishes = allDishes().filter((dish) => dish.chef === name);
  return dishes.length > 0 ? buildLiveChef(name, dishes) : undefined;
}

export function getDishesByChef(chefName: string): Dish[] {
  return allDishes().filter((dish) => dish.chef === chefName);
}
