"use client";

import { useState } from "react";
import type { NearbyKitchenDiscovery, NearbyMenuDiscovery } from "@/lib/discovery-contract";
import { formatDistance } from "@/lib/discovery-contract";
import { reverseGeocodeCurrentLocation } from "@/services/location/reverseGeocode";

type Mode = "kitchens" | "menu-items";
const EXISTING_DISCOVERY_RADIUS_METERS = 5000;

function money(amount: number, currency: string): string {
  try {
    return new Intl.NumberFormat("en-IN", { style: "currency", currency, maximumFractionDigits: 2 }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(2)}`;
  }
}

export function DiscoveryBrowser() {
  const [latitude, setLatitude] = useState<number | null>(null);
  const [longitude, setLongitude] = useState<number | null>(null);
  const [locationLabel, setLocationLabel] = useState("Choose your current location");
  const [mode, setMode] = useState<Mode>("menu-items");
  const [result, setResult] = useState<NearbyKitchenDiscovery | NearbyMenuDiscovery | null>(null);
  const [message, setMessage] = useState("Use your current location to discover nearby home food.");
  const [busy, setBusy] = useState(false);
  const [locating, setLocating] = useState(false);
  const [cartBusyId, setCartBusyId] = useState<string | null>(null);

  async function discoverAt(lat: number, lon: number, discoveryMode = mode) {
    setBusy(true);
    setResult(null);
    setMessage("Finding nearby home food…");
    try {
      const query = new URLSearchParams({
        latitude: String(lat),
        longitude: String(lon),
        radiusMeters: String(EXISTING_DISCOVERY_RADIUS_METERS),
        page: "0",
        size: "20",
      });
      const response = await fetch(`/api/discovery/${discoveryMode}?${query}`, { cache: "no-store" });
      const body = await response.json();
      if (!response.ok) throw new Error(body?.message || "Discovery failed");
      setResult(body);
      const count = discoveryMode === "kitchens" ? body.kitchens?.length ?? 0 : body.menuItems?.length ?? 0;
      setMessage(
        count
          ? `${count} nearby result${count === 1 ? "" : "s"} found around ${locationLabel === "Choose your current location" ? "your location" : locationLabel}.`
          : "No nearby results were found around this location.",
      );
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Discovery is unavailable right now.");
    } finally {
      setBusy(false);
    }
  }

  function detectLocation() {
    if (!navigator.geolocation) {
      setMessage("This browser does not provide location access.");
      return;
    }
    setLocating(true);
    setMessage("Detecting your location…");
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const lat = Number(position.coords.latitude.toFixed(7));
        const lon = Number(position.coords.longitude.toFixed(7));
        setLatitude(lat);
        setLongitude(lon);
        try {
          const detected = await reverseGeocodeCurrentLocation(lat, lon);
          const label = [detected.area, detected.city].filter(Boolean).join(", ") || detected.formattedAddress;
          setLocationLabel(label);
        } catch {
          setLocationLabel("Current location");
        } finally {
          setLocating(false);
        }
        await discoverAt(lat, lon);
      },
      () => {
        setLocating(false);
        setMessage("Location permission was not granted. Enable location access to see nearby food.");
      },
      { enableHighAccuracy: true, timeout: 12_000, maximumAge: 30_000 },
    );
  }

  async function refreshDiscovery(nextMode = mode) {
    if (latitude === null || longitude === null) {
      detectLocation();
      return;
    }
    await discoverAt(latitude, longitude, nextMode);
  }

  async function addToCart(menuItemId: string, itemName: string) {
    setCartBusyId(menuItemId);
    setMessage(`Adding ${itemName} to your cart…`);
    try {
      const response = await fetch("/api/cart/items", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ menuItemId, quantity: 1 }),
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body?.message || "Item could not be added.");
      setMessage(`${itemName} added. Your cart now has ${body.items?.length ?? 0} item${body.items?.length === 1 ? "" : "s"}.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Item could not be added.");
    } finally {
      setCartBusyId(null);
    }
  }

  return (
    <div className="space-y-6">
      <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950 shadow-2xl shadow-black/20 sm:p-8">
        <div className="flex flex-wrap gap-2">
          {(["menu-items", "kitchens"] as Mode[]).map((value) => (
            <button
              key={value}
              type="button"
              onClick={() => {
                setMode(value);
                if (latitude !== null && longitude !== null) void refreshDiscovery(value);
              }}
              className={`rounded-full px-4 py-2 text-sm font-bold ${mode === value ? "bg-[#6930CA] text-white" : "border border-[#6930CA] text-[#6930CA]"}`}
            >
              {value === "menu-items" ? "Nearby dishes" : "Home kitchens"}
            </button>
          ))}
          <a href="/cart" className="ml-auto rounded-full border border-[#6930CA] px-4 py-2 text-sm font-bold text-[#6930CA]">View cart</a>
        </div>

        <div className="mt-5 rounded-2xl border border-slate-200 bg-white p-4">
          <p className="text-xs font-bold uppercase tracking-[0.16em] text-slate-500">Delivering around</p>
          <p className="mt-1 text-lg font-bold">📍 {locationLabel}</p>
          <p className="mt-1 text-xs text-slate-500">Craves uses the map point in the background. Coordinates are never shown to customers.</p>
        </div>

        <div className="mt-5 flex flex-wrap gap-3">
          <button
            type="button"
            onClick={detectLocation}
            disabled={locating || busy}
            className="rounded-full bg-[#6930CA] px-6 py-3 text-sm font-bold text-white disabled:opacity-50"
          >
            {locating ? "Detecting location…" : latitude === null ? "Use my current location" : "Update current location"}
          </button>
          {latitude !== null && longitude !== null && (
            <button type="button" onClick={() => void refreshDiscovery()} disabled={busy} className="rounded-full border border-[#6930CA] px-5 py-3 text-sm font-bold text-[#6930CA] disabled:opacity-50">
              {busy ? "Discovering…" : "Refresh nearby food"}
            </button>
          )}
        </div>
        <p role="status" className="mt-4 text-sm text-slate-600">{message}</p>
      </section>

      {result && "menuItems" in result && (
        <section className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {result.menuItems.map((item) => (
            <article key={item.id} className="overflow-hidden rounded-[28px] bg-white shadow-xl shadow-black/15">
              {item.primaryImageUrl ? <img src={item.primaryImageUrl} alt="" className="h-48 w-full object-cover" referrerPolicy="no-referrer" /> : <div className="flex h-48 items-center justify-center bg-[#FFF8EC] text-5xl">🍲</div>}
              <div className="p-5 text-slate-950">
                <div className="flex items-start justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-wider text-[#6930CA]">{item.category} · {item.foodType.replace("_", " ")}</p><h2 className="mt-2 text-xl font-bold">{item.itemName}</h2></div><strong>{money(item.price, item.currency)}</strong></div>
                <p className="mt-3 line-clamp-2 text-sm leading-6 text-slate-600">{item.description ?? "Prepared by a nearby home kitchen."}</p>
                <div className="mt-4 flex items-center justify-between text-sm text-slate-600"><span>{item.kitchenDisplayName ?? item.kitchenName}</span><span>{formatDistance(item.distanceMeters)}</span></div>
                <p className="mt-2 text-xs text-slate-500">{item.areaName ? `${item.areaName}, ` : ""}{item.city}</p>
                <button type="button" disabled={cartBusyId === item.id} onClick={() => void addToCart(item.id, item.itemName)} className="mt-5 w-full rounded-full bg-[#6930CA] px-5 py-3 text-sm font-bold text-white disabled:opacity-50">{cartBusyId === item.id ? "Adding…" : "Add to cart"}</button>
              </div>
            </article>
          ))}
        </section>
      )}

      {result && "kitchens" in result && (
        <section className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
          {result.kitchens.map((kitchen) => (
            <article key={kitchen.id} className="rounded-[28px] bg-[#FFF8EC] p-6 text-slate-950 shadow-xl shadow-black/15">
              <p className="text-xs font-bold uppercase tracking-wider text-[#6930CA]">Home kitchen</p>
              <h2 className="mt-2 text-2xl font-bold">{kitchen.displayName ?? kitchen.kitchenName}</h2>
              <p className="mt-3 text-sm leading-6 text-slate-600">{kitchen.description ?? "Homemade food from a nearby Craves kitchen."}</p>
              <div className="mt-5 flex items-center justify-between text-sm"><span>{kitchen.areaName ? `${kitchen.areaName}, ` : ""}{kitchen.city}</span><strong>{formatDistance(kitchen.distanceMeters)}</strong></div>
              <p className="mt-2 text-sm text-slate-600">{kitchen.activeMenuItemCount} active dish{kitchen.activeMenuItemCount === 1 ? "" : "es"}</p>
            </article>
          ))}
        </section>
      )}
    </div>
  );
}
