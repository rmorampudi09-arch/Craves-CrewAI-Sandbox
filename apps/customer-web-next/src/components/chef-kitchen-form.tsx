"use client";

import { useEffect, useState } from "react";
import type { ChefApplication } from "@/lib/chef-application-contract";
import type {
  ChefKitchen,
  ChefKitchenInput,
  EditableKitchenStatus,
} from "@/lib/chef-kitchen-types";
import { reverseGeocodeCurrentLocation } from "@/services/location/reverseGeocode";

type FormState = Record<
  keyof Omit<ChefKitchenInput, "latitude" | "longitude" | "status">,
  string
> & {
  latitude: string;
  longitude: string;
  status: EditableKitchenStatus;
};

const EMPTY: FormState = {
  kitchenName: "",
  displayName: "",
  description: "",
  phoneNumber: "",
  email: "",
  addressLine1: "",
  addressLine2: "",
  landmark: "",
  areaName: "",
  city: "",
  state: "",
  postalCode: "",
  latitude: "",
  longitude: "",
  status: "DRAFT",
};

function fromKitchen(kitchen: ChefKitchen | null): FormState {
  if (!kitchen) return EMPTY;
  return {
    kitchenName: kitchen.kitchenName,
    displayName: kitchen.displayName ?? "",
    description: kitchen.description ?? "",
    phoneNumber: kitchen.phoneNumber ?? "",
    email: kitchen.email ?? "",
    addressLine1: kitchen.addressLine1,
    addressLine2: kitchen.addressLine2 ?? "",
    landmark: kitchen.landmark ?? "",
    areaName: kitchen.areaName ?? "",
    city: kitchen.city,
    state: kitchen.state,
    postalCode: kitchen.postalCode ?? "",
    latitude: kitchen.latitude === null ? "" : String(kitchen.latitude),
    longitude: kitchen.longitude === null ? "" : String(kitchen.longitude),
    status: kitchen.status === "SUSPENDED" ? "INACTIVE" : kitchen.status,
  };
}

function fromApplication(application: ChefApplication | null): FormState {
  if (!application || application.status !== "APPROVED") return EMPTY;
  return {
    ...EMPTY,
    displayName: [application.firstName, application.lastName].filter(Boolean).join(" "),
    email: application.email ?? "",
    addressLine1: application.addressLine1 ?? "",
    addressLine2: application.addressLine2 ?? "",
    landmark: application.landmark ?? "",
    city: application.city ?? "",
    state: application.state ?? "",
    postalCode: application.postalCode ?? "",
    latitude: application.latitude === null ? "" : String(application.latitude),
    longitude: application.longitude === null ? "" : String(application.longitude),
  };
}

export function ChefKitchenForm() {
  const [kitchen, setKitchen] = useState<ChefKitchen | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [message, setMessage] = useState("Loading your kitchen profile…");
  const [busy, setBusy] = useState(false);
  const [locating, setLocating] = useState(false);

  useEffect(() => {
    let active = true;
    void Promise.all([
      fetch("/api/chef/kitchen", { cache: "no-store" }),
      fetch("/api/chef/application", { cache: "no-store" }),
    ])
      .then(async ([kitchenResponse, applicationResponse]) => {
        const kitchenBody = await kitchenResponse.json().catch(() => null);
        const applicationBody = applicationResponse.ok
          ? ((await applicationResponse.json().catch(() => null)) as ChefApplication | null)
          : null;
        if (!active) return;
        if (!kitchenResponse.ok) {
          throw new Error(
            kitchenResponse.status === 403
              ? "An approved chef role is required. Sign out and sign in again after approval."
              : "Kitchen profile is temporarily unavailable.",
          );
        }
        const nextKitchen = kitchenBody as ChefKitchen | null;
        setKitchen(nextKitchen);
        setForm(nextKitchen ? fromKitchen(nextKitchen) : fromApplication(applicationBody));
        setMessage(
          nextKitchen
            ? ""
            : "Approved application details have been prefilled. Add a kitchen name and confirm the kitchen location before saving.",
        );
      })
      .catch((error) => {
        if (active) {
          setMessage(error instanceof Error ? error.message : "Kitchen profile is temporarily unavailable.");
        }
      });
    return () => {
      active = false;
    };
  }, []);

  function setField(name: keyof FormState, value: string) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  function useCurrentLocation() {
    if (!navigator.geolocation) {
      setMessage("This browser cannot provide a location.");
      return;
    }
    setLocating(true);
    setMessage("Detecting the kitchen address…");
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const latitude = Number(position.coords.latitude.toFixed(7));
        const longitude = Number(position.coords.longitude.toFixed(7));
        try {
          const detected = await reverseGeocodeCurrentLocation(latitude, longitude);
          setForm((current) => ({
            ...current,
            addressLine1: detected.houseNumber || detected.formattedAddress,
            addressLine2: detected.street || current.addressLine2,
            areaName: detected.area || detected.city || current.areaName,
            city: detected.city || current.city,
            state: detected.state || current.state,
            postalCode: detected.postalCode || current.postalCode,
            latitude: String(latitude),
            longitude: String(longitude),
          }));
          setMessage(
            detected.preciseHouseNumber
              ? `Kitchen address detected${detected.district ? ` in ${detected.district}` : ""}. Review the written details before saving.`
              : `Kitchen location detected${detected.district ? ` in ${detected.district}` : ""}. Please confirm or correct the house/building details.`,
          );
        } catch (error) {
          setForm((current) => ({
            ...current,
            latitude: String(latitude),
            longitude: String(longitude),
          }));
          setMessage(
            error instanceof Error
              ? `${error.message} The map point was captured; complete the written kitchen address manually.`
              : "The map point was captured but the written kitchen address could not be identified.",
          );
        } finally {
          setLocating(false);
        }
      },
      () => {
        setLocating(false);
        setMessage("Location permission was not granted.");
      },
      { enableHighAccuracy: true, timeout: 12_000, maximumAge: 30_000 },
    );
  }

  async function save() {
    if (form.status === "ACTIVE" && (!form.latitude.trim() || !form.longitude.trim())) {
      setMessage(
        "Use current location before activating this kitchen. Craves needs a mapped kitchen point for nearby discovery and delivery pickup.",
      );
      return;
    }
    setBusy(true);
    setMessage("Saving kitchen profile…");
    try {
      const body = {
        ...form,
        displayName: form.displayName || null,
        description: form.description || null,
        phoneNumber: form.phoneNumber || null,
        email: form.email || null,
        addressLine2: form.addressLine2 || null,
        landmark: form.landmark || null,
        areaName: form.areaName || null,
        postalCode: form.postalCode || null,
        latitude: form.latitude === "" ? null : Number(form.latitude),
        longitude: form.longitude === "" ? null : Number(form.longitude),
      };
      const response = await fetch("/api/chef/kitchen", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      const result = (await response.json().catch(() => null)) as { message?: unknown } | null;
      if (!response.ok) {
        throw new Error(
          typeof result?.message === "string"
            ? result.message
            : response.status === 400
              ? "Complete the required kitchen fields using valid values."
              : "Kitchen profile could not be saved.",
        );
      }
      setKitchen(result as unknown as ChefKitchen);
      setForm(fromKitchen(result as unknown as ChefKitchen));
      setMessage("Kitchen profile saved by Catalog Service.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Kitchen profile could not be saved.");
    } finally {
      setBusy(false);
    }
  }

  const suspended = kitchen?.status === "SUSPENDED";
  const mapped = Boolean(form.latitude.trim()) && Boolean(form.longitude.trim());
  const discoverable = form.status === "ACTIVE" && mapped;
  const fields: Array<[keyof FormState, string, boolean]> = [
    ["kitchenName", "Kitchen name", true],
    ["displayName", "Display name", false],
    ["description", "Description", false],
    ["phoneNumber", "Kitchen phone", false],
    ["email", "Kitchen email", false],
    ["addressLine1", "Flat / House / Building", true],
    ["addressLine2", "Street / Road", false],
    ["landmark", "Landmark", false],
    ["areaName", "Area", false],
    ["city", "City", true],
    ["state", "State", true],
    ["postalCode", "Pincode", false],
  ];

  return (
    <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950 sm:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.2em] text-[#6930CA]">Catalog kitchen</p>
          <h2 className="mt-2 text-3xl font-bold">{kitchen?.displayName ?? kitchen?.kitchenName ?? "Create kitchen"}</h2>
        </div>
        {kitchen && <span className="rounded-full bg-white px-4 py-2 text-sm font-bold">{kitchen.status}</span>}
      </div>
      {suspended && (
        <p className="mt-4 rounded-2xl bg-amber-50 p-4 text-sm text-amber-900">
          This kitchen is suspended. Profile changes remain blocked until the backend/admin state changes.
        </p>
      )}
      <div className={`mt-4 rounded-2xl p-4 text-sm ${discoverable ? "bg-green-50 text-green-900" : "bg-amber-50 text-amber-900"}`}>
        {discoverable
          ? "Kitchen location is mapped and eligible for nearby discovery after at least one menu item is ACTIVE and available."
          : "To appear to customers, set the kitchen ACTIVE and confirm its current location."}
      </div>
      <p role="status" className="mt-4 text-sm text-slate-600">{message}</p>
      <div className="mt-6">
        <button
          type="button"
          disabled={suspended || busy || locating}
          onClick={useCurrentLocation}
          className="w-full rounded-2xl border border-[#6930CA] bg-white px-4 py-3 text-sm font-bold text-[#6930CA] disabled:opacity-50 sm:w-auto"
        >
          {locating ? "Detecting kitchen address…" : mapped ? "Refresh kitchen location" : "Use current location"}
        </button>
        <p className="mt-2 text-xs text-slate-500">
          Craves stores the precise map point in the background. Chefs never need to enter latitude or longitude.
        </p>
      </div>
      <div className="mt-4 grid gap-4 md:grid-cols-2">
        {fields.map(([name, label, required]) => (
          <label key={name} className="text-sm font-semibold">
            {label}{required ? " *" : ""}
            {name === "description" ? (
              <textarea
                disabled={suspended || busy}
                value={form[name]}
                onChange={(event) => setField(name, event.target.value)}
                className="mt-2 min-h-28 w-full rounded-2xl border border-slate-300 bg-white px-4 py-3 disabled:bg-slate-100"
              />
            ) : (
              <input
                disabled={suspended || busy}
                value={form[name]}
                onChange={(event) => setField(name, event.target.value)}
                className="mt-2 w-full rounded-2xl border border-slate-300 bg-white px-4 py-3 disabled:bg-slate-100"
              />
            )}
          </label>
        ))}
      </div>
      <label className="mt-4 block text-sm font-semibold">
        Kitchen status
        <select
          disabled={suspended || busy}
          value={form.status}
          onChange={(event) => setField("status", event.target.value)}
          className="mt-2 w-full rounded-2xl border border-slate-300 bg-white px-4 py-3 md:max-w-xs"
        >
          <option value="DRAFT">Draft</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </label>
      <button
        type="button"
        disabled={suspended || busy || locating}
        onClick={() => void save()}
        className="mt-6 rounded-full bg-[#6930CA] px-6 py-3 font-bold text-white disabled:opacity-50"
      >
        Save kitchen profile
      </button>
    </section>
  );
}
