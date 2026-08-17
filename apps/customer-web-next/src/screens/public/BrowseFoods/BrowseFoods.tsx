import { useNavigate } from "@tanstack/react-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { ArrowLeft, MapPin, Utensils } from "lucide-react";

import { BrowseHeader } from "@/components/home/BrowseHeader";
import { WelcomeBanner } from "@/components/home/WelcomeBanner";
import { CategoryFilterChips } from "@/components/home/CategoryFilterChips";
import { DishesGrid } from "@/components/home/DishesGrid";
import { KitchensGrid } from "@/components/home/KitchensGrid";
import { FloatingCartBar } from "@/components/home/FloatingCartBar";
import {
  ALL_DISHES_CATEGORY,
  type DishCategory,
} from "@/constants/dishCategories";
import {
  loadKitchenMenu,
  type Dish,
} from "@/services/api/dishes";
import { discoverKitchens } from "@/services/api/kitchens";
import { formatDiscoveryRadius } from "@/lib/catalog-discovery-policy";
import {
  type NearbyKitchen,
} from "@/lib/discovery-contract";
import {
  parseLocationRecommendation,
  type CustomerAddress,
} from "@/lib/address-contract";
import {
  clearSession,
  loadSelectedAddress,
  loadSession,
  saveAddress,
  type CravesAddress,
  type CravesUser,
} from "@/services/auth/cravesAuth";
import { reverseGeocodeCurrentLocation } from "@/services/location/reverseGeocode";
import { cartCount, loadCart, subscribeCart } from "@/services/api/cravesCart";

type DiscoveryState = "loading" | "ready" | "error" | "address-required";
const SAVED_ADDRESS_MATCH_RADIUS_METERS = 100;

export const routeMeta = {
  head: () => ({
    meta: [
      { title: "Discover Homemade Food – Craves" },
      {
        name: "description",
        content: "Discover nearby Craves home kitchens and open a kitchen to browse its live menu.",
      },
      { name: "robots", content: "noindex" },
    ],
  }),
};

function savedAddressToBrowsingLocation(address: CustomerAddress): CravesAddress | null {
  if (address.latitude == null || address.longitude == null || !address.areaName) return null;
  return {
    id: address.id,
    label: address.addressLabel,
    hno: address.addressLine1,
    street: address.addressLine2 ?? address.landmark ?? undefined,
    city: address.city,
    mandal: address.areaName,
    district: address.districtName ?? address.city,
    pincode: address.postalCode ?? undefined,
    lat: address.latitude,
    lng: address.longitude,
  };
}

function readCurrentPosition(): Promise<GeolocationPosition> {
  return new Promise((resolve, reject) => {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      reject(new Error("Location access is unavailable."));
      return;
    }
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: true,
      timeout: 12_000,
      maximumAge: 30_000,
    });
  });
}

async function resolveLiveBrowsingLocation(
  fallback: CravesAddress | null,
): Promise<CravesAddress | null> {
  let position: GeolocationPosition;
  try {
    position = await readCurrentPosition();
  } catch {
    return fallback;
  }

  const latitude = Number(position.coords.latitude.toFixed(7));
  const longitude = Number(position.coords.longitude.toFixed(7));
  const query = new URLSearchParams({
    latitude: String(latitude),
    longitude: String(longitude),
    matchRadiusMeters: String(SAVED_ADDRESS_MATCH_RADIUS_METERS),
  });

  try {
    const recommendationResponse = await fetch(
      `/api/customer/addresses/recommendation?${query}`,
      { cache: "no-store", credentials: "same-origin" },
    );
    if (recommendationResponse.ok) {
      const recommendation = parseLocationRecommendation(
        await recommendationResponse.json().catch(() => null),
      );
      if (recommendation?.selectedSavedAddress) {
        const matched = savedAddressToBrowsingLocation(recommendation.selectedSavedAddress);
        if (matched) {
          saveAddress(matched);
          return matched;
        }
      }
    }
  } catch {
    // A saved-address recommendation is an optimization. Once GPS succeeded,
    // discovery must continue from the live point even if this lookup fails.
  }

  try {
    const detected = await reverseGeocodeCurrentLocation(latitude, longitude);
    const live: CravesAddress = {
      label: "CURRENT LOCATION",
      hno: detected.houseNumber || detected.formattedAddress,
      street: detected.street ?? undefined,
      city: detected.city || "",
      mandal: detected.area || detected.city || "Current location",
      district: detected.district || detected.city || "",
      pincode: detected.postalCode ?? undefined,
      lat: latitude,
      lng: longitude,
    };
    saveAddress(live);
    return live;
  } catch {
    const liveWithoutAddress: CravesAddress = {
      label: "CURRENT LOCATION",
      hno: "Current location",
      city: "",
      mandal: "Current location",
      district: "",
      pincode: undefined,
      lat: latitude,
      lng: longitude,
    };
    saveAddress(liveWithoutAddress);
    return liveWithoutAddress;
  }
}

function BrowseFoodsPage() {
  const navigate = useNavigate();
  const [user, setUser] = useState<CravesUser | null>(null);
  const [address, setAddress] = useState<CravesAddress | null>(null);
  const [category, setCategory] = useState<DishCategory>(ALL_DISHES_CATEGORY);
  const [searchTerm, setSearchTerm] = useState("");
  const [cartItemCount, setCartItemCount] = useState(0);
  const [kitchens, setKitchens] = useState<NearbyKitchen[]>([]);
  const [selectedKitchen, setSelectedKitchen] = useState<NearbyKitchen | null>(null);
  const [dishes, setDishes] = useState<Dish[]>([]);
  const [discoveryState, setDiscoveryState] = useState<DiscoveryState>("loading");
  const [catalogMessage, setCatalogMessage] = useState("Detecting your current delivery location…");
  const [radiusLabel, setRadiusLabel] = useState<string | null>(null);

  const refreshDiscovery = useCallback(async (activeAddress: CravesAddress | null) => {
    setSelectedKitchen(null);
    setDishes([]);
    setCategory(ALL_DISHES_CATEGORY);
    setSearchTerm("");

    if (
      typeof activeAddress?.lat !== "number" ||
      typeof activeAddress.lng !== "number"
    ) {
      setKitchens([]);
      setRadiusLabel(null);
      setDiscoveryState("address-required");
      setCatalogMessage(
        "Choose or save a delivery location so Craves can show nearby home kitchens.",
      );
      return;
    }

    setDiscoveryState("loading");
    setCatalogMessage("Loading nearby active home kitchens…");
    try {
      const result = await discoverKitchens(activeAddress.lat, activeAddress.lng, 5_000);
      setKitchens(result.kitchens);
      setRadiusLabel(formatDiscoveryRadius(result.radiusMeters));
      setDiscoveryState("ready");
      setCatalogMessage(
        result.kitchens.length === 0
          ? `No active home kitchens were returned within ${formatDiscoveryRadius(result.radiusMeters)} of this location.`
          : `Choose one of the nearby home kitchens within ${formatDiscoveryRadius(result.radiusMeters)} to view its menu.`,
      );
    } catch (error) {
      setKitchens([]);
      setRadiusLabel(null);
      setDiscoveryState("error");
      setCatalogMessage(
        error instanceof Error
          ? error.message
          : "Nearby kitchens are temporarily unavailable.",
      );
    }
  }, []);

  const openKitchen = useCallback(async (kitchen: NearbyKitchen) => {
    const kitchenName = kitchen.displayName || kitchen.kitchenName;
    setSelectedKitchen(kitchen);
    setDishes([]);
    setCategory(ALL_DISHES_CATEGORY);
    setSearchTerm("");
    setDiscoveryState("loading");
    setCatalogMessage(`Loading ${kitchenName}'s live menu…`);

    try {
      const menu = await loadKitchenMenu(kitchen.id);
      const locatedMenu = menu.map((dish) => ({
        ...dish,
        distanceMeters: kitchen.distanceMeters,
        areaName: dish.areaName ?? kitchen.areaName ?? undefined,
        city: dish.city ?? kitchen.city,
        state: dish.state ?? kitchen.state,
      }));
      setDishes(locatedMenu);
      setDiscoveryState("ready");
      setCatalogMessage(
        locatedMenu.length === 0
          ? `${kitchenName} does not have any active dishes right now.`
          : `Showing ${kitchenName}'s live menu.`,
      );
    } catch (error) {
      setDishes([]);
      setDiscoveryState("error");
      setCatalogMessage(
        error instanceof Error
          ? error.message
          : "This kitchen's menu is temporarily unavailable.",
      );
    }
  }, []);

  useEffect(() => {
    let active = true;

    void (async () => {
      const current = await loadSession();
      if (!active) return;
      if (!current) {
        navigate({ to: "/", replace: true });
        return;
      }
      setUser(current);

      try {
        const savedFallback = await loadSelectedAddress();
        if (!active) return;
        setAddress(savedFallback);
        setCatalogMessage(
          savedFallback
            ? "Checking whether you are still near your saved address…"
            : "Detecting your current delivery location…",
        );
        const activeLocation = await resolveLiveBrowsingLocation(savedFallback);
        if (!active) return;
        setAddress(activeLocation);
        await refreshDiscovery(activeLocation);
      } catch (error) {
        if (!active) return;
        setAddress(null);
        setKitchens([]);
        setSelectedKitchen(null);
        setDishes([]);
        setDiscoveryState("error");
        setCatalogMessage(
          error instanceof Error
            ? error.message
            : "Your delivery location could not be loaded.",
        );
      }

      try {
        await loadCart();
        if (active) setCartItemCount(cartCount());
      } catch {
        if (active) setCartItemCount(0);
      }
    })();

    const unsubscribeCart = subscribeCart(() => setCartItemCount(cartCount()));
    return () => {
      active = false;
      unsubscribeCart();
    };
  }, [navigate, refreshDiscovery]);

  const categories = useMemo<readonly DishCategory[]>(() => {
    const live = Array.from(
      new Set(dishes.map((dish) => dish.category.trim()).filter(Boolean)),
    ).sort((left, right) => left.localeCompare(right));
    return [ALL_DISHES_CATEGORY, ...live];
  }, [dishes]);

  useEffect(() => {
    if (!categories.includes(category)) setCategory(ALL_DISHES_CATEGORY);
  }, [categories, category]);

  const filteredKitchens = useMemo(() => {
    const term = searchTerm.trim().toLocaleLowerCase("en-IN");
    if (!term) return kitchens;
    return kitchens.filter((kitchen) =>
      [
        kitchen.displayName,
        kitchen.kitchenName,
        kitchen.description,
        kitchen.areaName,
        kitchen.city,
        kitchen.state,
      ].some((value) => value?.toLocaleLowerCase("en-IN").includes(term)),
    );
  }, [searchTerm, kitchens]);

  const filteredDishes = useMemo(() => {
    const term = searchTerm.trim().toLocaleLowerCase("en-IN");
    return dishes.filter((dish) => {
      const categoryMatches =
        category === ALL_DISHES_CATEGORY || dish.category === category;
      const searchMatches =
        !term ||
        dish.name.toLocaleLowerCase("en-IN").includes(term) ||
        dish.chef.toLocaleLowerCase("en-IN").includes(term) ||
        dish.category.toLocaleLowerCase("en-IN").includes(term) ||
        dish.desc.toLocaleLowerCase("en-IN").includes(term);
      return categoryMatches && searchMatches;
    });
  }, [category, searchTerm, dishes]);

  const handleLogout = async () => {
    await clearSession();
    navigate({ to: "/" });
  };

  const locationLabel = address
    ? [address.mandal, address.city].filter(Boolean).join(", ")
    : "Set delivery location";

  const visibleDishCount = selectedKitchen
    ? dishes.length
    : kitchens.reduce((total, kitchen) => total + kitchen.activeMenuItemCount, 0);

  if (!user) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-white">
        <p className="text-sm font-medium text-muted-foreground" role="status">
          Loading your Craves session…
        </p>
      </main>
    );
  }

  return (
    <div className="min-h-screen bg-white pb-24 text-ink">
      <BrowseHeader
        user={user}
        locationLabel={locationLabel}
        onOpenLocation={() => navigate({ to: "/addresses" })}
        cartCount={cartItemCount}
        onOpenCart={() => navigate({ to: "/cart" })}
        onLogout={handleLogout}
        searchTerm={searchTerm}
        onSearchTermChange={setSearchTerm}
      />
      <main>
        <WelcomeBanner
          firstName={user.firstName || user.username.split(" ")[0] || "there"}
          dishCount={visibleDishCount}
          radiusLabel={radiusLabel}
          hasAddress={Boolean(address?.lat != null && address?.lng != null)}
        />

        {selectedKitchen ? (
          <>
            <section className="mx-auto max-w-7xl px-4 pt-6 md:px-6" aria-label="Selected kitchen">
              <button
                type="button"
                onClick={() => {
                  setSelectedKitchen(null);
                  setDishes([]);
                  setCategory(ALL_DISHES_CATEGORY);
                  setSearchTerm("");
                  setDiscoveryState("ready");
                  setCatalogMessage(
                    radiusLabel
                      ? `Choose one of the nearby home kitchens within ${radiusLabel} to view its menu.`
                      : "Choose a nearby home kitchen to view its menu.",
                  );
                }}
                className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-primary px-4 text-sm font-semibold text-contrast-red hover:bg-secondary"
              >
                <ArrowLeft className="h-4 w-4" aria-hidden="true" />
                Nearby kitchens
              </button>

              <div className="mt-4 rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:flex md:items-center md:justify-between md:gap-6">
                <div>
                  <p className="craves-overline text-primary">Selected home kitchen</p>
                  <h2 className="mt-1 font-display text-2xl font-bold tracking-[-0.035em] text-ink">
                    {selectedKitchen.displayName || selectedKitchen.kitchenName}
                  </h2>
                  <p className="mt-2 flex items-center gap-1.5 text-sm text-muted-foreground">
                    <MapPin className="h-4 w-4 text-primary" aria-hidden="true" />
                    {[selectedKitchen.areaName, selectedKitchen.city]
                      .filter(Boolean)
                      .join(", ")}
                  </p>
                </div>
                <div className="mt-4 inline-flex items-center gap-2 rounded-full bg-secondary px-3 py-2 text-sm font-semibold text-ink md:mt-0">
                  <Utensils className="h-4 w-4 text-primary" aria-hidden="true" />
                  {selectedKitchen.activeMenuItemCount} active {selectedKitchen.activeMenuItemCount === 1 ? "dish" : "dishes"}
                </div>
              </div>
            </section>

            <CategoryFilterChips
              categories={categories}
              selected={category}
              onSelect={setCategory}
            />
            <DishesGrid
              dishes={filteredDishes}
              selectedCategory={category}
              searchTerm={searchTerm}
              state={discoveryState}
              message={catalogMessage}
              onRetry={() => void openKitchen(selectedKitchen)}
              onManageAddress={() => navigate({ to: "/addresses" })}
            />
          </>
        ) : (
          <KitchensGrid
            kitchens={filteredKitchens}
            searchTerm={searchTerm}
            state={discoveryState}
            message={catalogMessage}
            onSelectKitchen={(kitchen) => void openKitchen(kitchen)}
            onRetry={() => void refreshDiscovery(address)}
            onManageAddress={() => navigate({ to: "/addresses" })}
          />
        )}
      </main>
      <FloatingCartBar
        itemCount={cartItemCount}
        onViewCart={() => navigate({ to: "/cart" })}
      />
    </div>
  );
}

export default BrowseFoodsPage;
