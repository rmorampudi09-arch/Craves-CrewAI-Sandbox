"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import {
  isDeliveryReadyAddress,
  parseAddressInput,
  type AddressLabel,
  type CustomerAddress,
  type CustomerAddressInput,
  type LocationRecommendation,
} from "@/lib/address-contract";
import { reverseGeocodeCurrentLocation } from "@/services/location/reverseGeocode";

type AddressForm = Omit<CustomerAddressInput, "latitude" | "longitude"> & {
  latitude: number | "";
  longitude: number | "";
};

const empty: AddressForm = {
  addressLabel: "HOME",
  recipientName: "",
  contactPhoneNumber: "",
  addressLine1: "",
  addressLine2: null,
  landmark: null,
  areaName: "",
  districtName: "",
  city: "",
  state: "",
  postalCode: "",
  latitude: "",
  longitude: "",
  isDefault: false,
};

function addressToForm(address: CustomerAddress): AddressForm {
  return {
    addressLabel: address.addressLabel,
    recipientName: address.recipientName ?? "",
    contactPhoneNumber: address.contactPhoneNumber,
    addressLine1: address.addressLine1,
    addressLine2: address.addressLine2,
    landmark: address.landmark,
    areaName: address.areaName ?? "",
    districtName: address.districtName ?? "",
    city: address.city,
    state: address.state,
    postalCode: address.postalCode ?? "",
    latitude: address.latitude ?? "",
    longitude: address.longitude ?? "",
    isDefault: address.isDefault,
  };
}

export function CustomerAddresses() {
  const [addresses, setAddresses] = useState<CustomerAddress[]>([]);
  const [form, setForm] = useState<AddressForm>(empty);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [message, setMessage] = useState("Loading saved addresses…");
  const [busy, setBusy] = useState(false);
  const [locating, setLocating] = useState(false);

  const load = useCallback(async () => {
    const response = await fetch("/api/customer/addresses", {
      cache: "no-store",
      credentials: "same-origin",
    });
    const body = await response.json().catch(() => null);
    if (!response.ok) throw new Error(body?.message || "Addresses could not be loaded.");
    setAddresses(body);
    const incomplete = body.filter(
      (address: CustomerAddress) => !isDeliveryReadyAddress(address),
    ).length;
    setMessage(
      incomplete
        ? `${body.length} saved address${body.length === 1 ? "" : "es"}; ${incomplete} need completion before checkout.`
        : body.length
          ? `${body.length} saved address${body.length === 1 ? "" : "es"}.`
          : "No saved addresses yet.",
    );
  }, []);

  useEffect(() => {
    load().catch((error) => setMessage(
      error instanceof Error ? error.message : "Addresses could not be loaded.",
    ));
  }, [load]);

  function setField<K extends keyof AddressForm>(key: K, value: AddressForm[K]) {
    setForm((previous) => ({ ...previous, [key]: value }));
  }

  function useLocation() {
    if (!navigator.geolocation) {
      setMessage("This browser cannot provide your current location.");
      return;
    }
    setLocating(true);
    setMessage("Detecting your current delivery address…");
    navigator.geolocation.getCurrentPosition(async (position) => {
      const latitude = Number(position.coords.latitude.toFixed(7));
      const longitude = Number(position.coords.longitude.toFixed(7));
      try {
        const query = new URLSearchParams({
          latitude: String(latitude),
          longitude: String(longitude),
          matchRadiusMeters: "100",
        });
        const recommendationResponse = await fetch(
          `/api/customer/addresses/recommendation?${query}`,
          { cache: "no-store", credentials: "same-origin" },
        );
        const recommendation = recommendationResponse.ok
          ? await recommendationResponse.json() as LocationRecommendation
          : null;

        if (recommendation?.selectedSavedAddress) {
          const saved = recommendation.selectedSavedAddress;
          setEditingId(saved.id);
          setForm(addressToForm(saved));
          setMessage(
            `You're near your saved ${saved.addressLabel.toLowerCase()} address. Review it and update only if needed.`,
          );
          return;
        }

        const detected = await reverseGeocodeCurrentLocation(latitude, longitude);
        setForm((previous) => ({
          ...previous,
          addressLine1: detected.houseNumber || detected.formattedAddress,
          addressLine2: detected.street || previous.addressLine2,
          areaName: detected.area || detected.city || previous.areaName,
          districtName: detected.district || detected.city || previous.districtName,
          city: detected.city || previous.city,
          state: detected.state || previous.state,
          postalCode: detected.postalCode || previous.postalCode,
          latitude,
          longitude,
        }));
        setMessage(
          detected.preciseHouseNumber
            ? "Address detected. House/building, street, area, district, city, state and pincode were filled automatically. Please correct anything that differs from your door address."
            : "Location detected and the available postal address was filled automatically. Please confirm or correct the house/flat/building before saving.",
        );
      } catch (error) {
        setForm((previous) => ({ ...previous, latitude, longitude }));
        setMessage(
          error instanceof Error
            ? `${error.message} You can still enter the written address manually.`
            : "Location captured, but the written address could not be identified.",
        );
      } finally {
        setLocating(false);
      }
    }, () => {
      setLocating(false);
      setMessage("Location permission was not granted. You can enter the address manually.");
    }, {
      enableHighAccuracy: true,
      timeout: 12_000,
      maximumAge: 30_000,
    });
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const input = parseAddressInput(form);
    if (!input) {
      setMessage("Confirm a complete delivery address and use current location so Craves can map the drop-off point.");
      return;
    }
    setBusy(true);
    setMessage(editingId ? "Updating address…" : "Saving address…");
    try {
      const response = await fetch(
        editingId ? `/api/customer/addresses/${editingId}` : "/api/customer/addresses",
        {
          method: editingId ? "PUT" : "POST",
          credentials: "same-origin",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(input),
        },
      );
      const body = await response.json().catch(() => null);
      if (!response.ok) throw new Error(body?.message || "Address could not be saved.");
      setForm(empty);
      setEditingId(null);
      await load();
      setMessage("Address saved.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Address could not be saved.");
    } finally {
      setBusy(false);
    }
  }

  function edit(address: CustomerAddress) {
    setEditingId(address.id);
    setForm(addressToForm(address));
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  async function remove(addressId: string) {
    if (!window.confirm("Delete this saved address?")) return;
    setBusy(true);
    try {
      const response = await fetch(`/api/customer/addresses/${addressId}`, {
        method: "DELETE",
        credentials: "same-origin",
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body?.message || "Address could not be deleted.");
      }
      await load();
      setMessage("Address deleted.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Address could not be deleted.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid gap-8 lg:grid-cols-[0.95fr_1.05fr]">
      <form onSubmit={submit} className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950 shadow-2xl shadow-black/20 sm:p-8">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-[#6930CA]">
          {editingId ? "Edit address" : "Add address"}
        </p>
        <button
          type="button"
          onClick={useLocation}
          disabled={locating || busy}
          className="mt-5 w-full rounded-2xl border border-slate-300 bg-white px-5 py-4 text-left text-sm font-bold disabled:opacity-50"
        >
          {locating ? "Detecting your address…" : "📍 Use my current location"}
          <span className="mt-1 block text-xs font-normal text-slate-500">
            Craves will fill the available house/building, street, area, district, city, state and pincode automatically.
          </span>
        </button>
        <div className="mt-5 grid gap-4 sm:grid-cols-2">
          <label className="text-sm font-semibold">Label<select className="mt-2 w-full rounded-2xl border border-slate-300 bg-white px-4 py-3" value={form.addressLabel} onChange={(event) => setField("addressLabel", event.target.value as AddressLabel)}>{["HOME", "WORK", "OTHER"].map((label) => <option key={label}>{label}</option>)}</select></label>
          <label className="text-sm font-semibold">Recipient name<input required maxLength={160} className="mt-2 w-full rounded-2xl border border-slate-300 px-4 py-3" value={form.recipientName} onChange={(event) => setField("recipientName", event.target.value)} /></label>
          <label className="text-sm font-semibold">Contact phone<input required inputMode="tel" maxLength={16} className="mt-2 w-full rounded-2xl border border-slate-300 px-4 py-3" value={form.contactPhoneNumber} onChange={(event) => setField("contactPhoneNumber", event.target.value)} /></label>
          <label className="text-sm font-semibold sm:col-span-2">Flat / House / Building<input required maxLength={250} className="mt-2 w-full rounded-2xl border border-slate-300 px-4 py-3" value={form.addressLine1} onChange={(event) => setField("addressLine1", event.target.value)} /></label>
          <label className="text-sm font-semibold sm:col-span-2">Street / Road<input maxLength={250} className="mt-2 w-full rounded-2xl border border-slate-300 px-4 py-3" value={form.addressLine2 ?? ""} onChange={(event) => setField("addressLine2", event.target.value || null)} /></label>
          <label className="text-sm font-semibold">Area<input required maxLength={120} className="mt-2 w-full rounded-2xl border border-slate-300 px-4 py-3" value={form.areaName} onChange={(event) => setField("areaName", event.target.value)} /></label>
          <label className="text-sm font-semibold">District<input required maxLength={120} className="mt-2 w-full rounded-2xl border border-slate-300 px-4 py-3" value={form.districtName} onChange={(event) => setField("districtName", event.target.value)} /></label>
          <label className="text-sm font-semibold">City<input required maxLength={120} className="mt-2 w-full rounded-2xl border border-slate-300 px-4 py-3" value={form.city} onChange={(event) => setField("city", event.target.value)} /></label>
          <label className="text-sm font-semibold">State<input required maxLength={120} className="mt-2 w-full rounded-2xl border border-slate-300 px-4 py-3" value={form.state} onChange={(event) => setField("state", event.target.value)} /></label>
          <label className="text-sm font-semibold">Pincode<input required maxLength={20} inputMode="numeric" className="mt-2 w-full rounded-2xl border border-slate-300 px-4 py-3" value={form.postalCode} onChange={(event) => setField("postalCode", event.target.value)} /></label>
          <label className="text-sm font-semibold">Landmark (optional)<input maxLength={160} className="mt-2 w-full rounded-2xl border border-slate-300 px-4 py-3" value={form.landmark ?? ""} onChange={(event) => setField("landmark", event.target.value || null)} /></label>
        </div>
        <p className="mt-4 rounded-2xl bg-white/70 p-3 text-xs leading-5 text-slate-600">
          The map coordinates are captured securely in the background for discovery and delivery. Customers never need to type or manage latitude/longitude.
        </p>
        <label className="mt-4 flex items-center gap-3 text-sm font-semibold"><input type="checkbox" checked={form.isDefault} onChange={(event) => setField("isDefault", event.target.checked)} />Use as default address</label>
        <div className="mt-5 flex flex-wrap gap-3">
          <button disabled={busy || locating} className="rounded-full bg-[#6930CA] px-6 py-3 text-sm font-bold text-white disabled:opacity-50">{editingId ? "Update address" : "Save address"}</button>
          {editingId && <button type="button" onClick={() => { setEditingId(null); setForm(empty); }} className="rounded-full px-5 py-3 text-sm font-bold text-slate-600">Cancel</button>}
        </div>
        <p role="status" className="mt-4 text-sm text-slate-600">{message}</p>
      </form>

      <section className="space-y-4">
        {addresses.map((address) => {
          const ready = isDeliveryReadyAddress(address);
          return (
            <article key={address.id} className="rounded-[28px] bg-white p-6 text-slate-950 shadow-xl shadow-black/15">
              <div className="flex items-start justify-between gap-4"><div><p className="text-xs font-bold uppercase tracking-wider text-[#6930CA]">{address.addressLabel}{address.isDefault ? " · DEFAULT" : ""}{ready ? "" : " · UPDATE REQUIRED"}</p><h2 className="mt-2 text-xl font-bold">{address.recipientName ?? "Saved address"}</h2></div><span className="text-sm text-slate-500">{address.contactPhoneNumber}</span></div>
              <p className="mt-4 text-sm leading-6 text-slate-700">{[address.addressLine1, address.addressLine2, address.landmark, address.areaName, address.districtName, address.city, address.state, address.postalCode].filter(Boolean).join(", ")}</p>
              {!ready && <p className="mt-3 rounded-xl bg-amber-50 p-3 text-xs text-amber-900">Complete this older address before using it at checkout.</p>}
              <div className="mt-5 flex gap-3"><button type="button" onClick={() => edit(address)} className="rounded-full border border-[#6930CA] px-4 py-2 text-sm font-bold text-[#6930CA]">Edit</button><button type="button" onClick={() => void remove(address.id)} disabled={busy} className="rounded-full border border-red-300 px-4 py-2 text-sm font-bold text-red-700 disabled:opacity-50">Delete</button></div>
            </article>
          );
        })}
      </section>
    </div>
  );
}
