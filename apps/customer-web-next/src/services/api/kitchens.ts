import { candidateDiscoveryRadii } from "@/lib/catalog-discovery-policy";
import {
  parseKitchenDiscovery,
  type NearbyKitchen,
} from "@/lib/discovery-contract";

export type KitchenDiscoveryResult = {
  kitchens: NearbyKitchen[];
  radiusMeters: number;
};

export async function discoverKitchens(
  latitude: number,
  longitude: number,
  radiusMeters = 5_000,
): Promise<KitchenDiscoveryResult> {
  let usedRadius = radiusMeters;

  for (const candidateRadius of candidateDiscoveryRadii(radiusMeters)) {
    usedRadius = candidateRadius;
    const query = new URLSearchParams({
      latitude: String(latitude),
      longitude: String(longitude),
      radiusMeters: String(candidateRadius),
      page: "0",
      size: "50",
    });

    const response = await fetch(`/api/discovery/kitchens?${query}`, {
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
          : "Nearby kitchens are temporarily unavailable.";
      throw new Error(message);
    }

    const payload = parseKitchenDiscovery(body);
    if (!payload) {
      throw new Error("Craves returned an invalid kitchen discovery response.");
    }

    if (payload.kitchens.length > 0) {
      return { kitchens: payload.kitchens, radiusMeters: candidateRadius };
    }
  }

  return { kitchens: [], radiusMeters: usedRadius };
}
