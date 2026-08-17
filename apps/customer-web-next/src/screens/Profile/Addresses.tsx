"use client";

import { useEffect, useState } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import {
  AlertTriangle,
  ArrowLeft,
  Check,
  Crosshair,
  Loader2,
  MapPin,
  Pencil,
  Plus,
  Trash2,
  X,
} from "lucide-react";
import {
  isDeliveryReadyAddress,
  parseAddressInput,
  type AddressLabel,
  type CustomerAddress,
  type CustomerAddressInput,
} from "@/lib/address-contract";
import { loadSession } from "@/services/auth/cravesAuth";
import { reverseGeocodeCurrentLocation } from "@/services/location/reverseGeocode";

type AddressDraft = Omit<CustomerAddressInput, "latitude" | "longitude"> & {
  latitude: string;
  longitude: string;
};

const blank: AddressDraft = {
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

function draftFrom(address: CustomerAddress): AddressDraft {
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
    latitude: address.latitude == null ? "" : String(address.latitude),
    longitude: address.longitude == null ? "" : String(address.longitude),
    isDefault: address.isDefault,
  };
}

function addressLine(address: CustomerAddress): string {
  return [
    address.addressLine1,
    address.addressLine2,
    address.landmark,
    address.areaName,
    address.districtName,
    address.city,
    address.state,
    address.postalCode,
  ].filter(Boolean).join(", ");
}

function recipientLine(address: CustomerAddress): string {
  return [address.recipientName, address.contactPhoneNumber].filter(Boolean).join(" · ");
}

export default function AddressesPage() {
  const navigate = useNavigate();
  const [addresses, setAddresses] = useState<CustomerAddress[]>([]);
  const [draft, setDraft] = useState<AddressDraft>(blank);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [locating, setLocating] = useState(false);
  const [message, setMessage] = useState("Loading saved addresses…");

  async function load() {
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
      incomplete > 0
        ? `${body.length} saved address${body.length === 1 ? "" : "es"}; ${incomplete} need${incomplete === 1 ? "s" : ""} completion before checkout.`
        : body.length
          ? `${body.length} saved address${body.length === 1 ? "" : "es"}.`
          : "No addresses saved yet.",
    );
  }

  useEffect(() => {
    void (async () => {
      if (!await loadSession()) {
        navigate({ to: "/" });
        return;
      }
      await load();
    })().catch((error) => setMessage(
      error instanceof Error ? error.message : "Addresses could not be loaded.",
    ));
  }, [navigate]);

  function update<K extends keyof AddressDraft>(key: K, value: AddressDraft[K]) {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  function beginCreate() {
    setEditingId(null);
    setDraft(blank);
    setOpen(true);
    setMessage("Use current location and Craves will fill the address for you.");
  }

  function beginEdit(address: CustomerAddress) {
    setEditingId(address.id);
    setDraft(draftFrom(address));
    setOpen(true);
  }

  function captureLocation() {
    if (!navigator.geolocation) {
      setMessage("This browser does not support location access.");
      return;
    }
    setLocating(true);
    setMessage("Detecting your current delivery address…");
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const latitude = Number(position.coords.latitude.toFixed(7));
        const longitude = Number(position.coords.longitude.toFixed(7));
        try {
          const detected = await reverseGeocodeCurrentLocation(latitude, longitude);
          setDraft((current) => ({
            ...current,
            addressLine1: detected.houseNumber || detected.formattedAddress,
            addressLine2: detected.street || current.addressLine2,
            areaName: detected.area || detected.city || current.areaName,
            districtName: detected.district || detected.city || current.districtName,
            city: detected.city || current.city,
            state: detected.state || current.state,
            postalCode: detected.postalCode || current.postalCode,
            latitude: String(latitude),
            longitude: String(longitude),
          }));
          setMessage(
            detected.preciseHouseNumber
              ? "Address detected and filled automatically. Confirm or correct the flat/house/building before saving."
              : "Location detected and available address fields were filled. Please confirm or correct the flat/house/building.",
          );
        } catch (error) {
          setDraft((current) => ({
            ...current,
            latitude: String(latitude),
            longitude: String(longitude),
          }));
          setMessage(
            error instanceof Error
              ? `${error.message} You can still complete the written address manually.`
              : "Location captured but the written address could not be identified.",
          );
        } finally {
          setLocating(false);
        }
      },
      () => {
        setLocating(false);
        setMessage("Location permission was not granted. You can enter the written address manually.");
      },
      { enableHighAccuracy: true, timeout: 12_000, maximumAge: 30_000 },
    );
  }

  async function save() {
    const input = parseAddressInput({
      ...draft,
      addressLine2: draft.addressLine2?.trim() || null,
      landmark: draft.landmark?.trim() || null,
      latitude: draft.latitude.trim(),
      longitude: draft.longitude.trim(),
    });
    if (!input) {
      setMessage("Complete the recipient, phone and written address, then use current location so Craves can map the drop-off point.");
      return;
    }

    setBusy(true);
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
      setOpen(false);
      setEditingId(null);
      setDraft(blank);
      await load();
      setMessage("Address saved.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Address could not be saved.");
    } finally {
      setBusy(false);
    }
  }

  async function remove(address: CustomerAddress) {
    if (!window.confirm(`Delete ${address.addressLabel.toLowerCase()} address?`)) return;
    setBusy(true);
    try {
      const response = await fetch(`/api/customer/addresses/${address.id}`, {
        method: "DELETE",
        credentials: "same-origin",
      });
      if (!response.ok) {
        const body = await response.json().catch(() => null);
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
    <div className="min-h-screen bg-cream pb-12">
      <header className="border-b border-border bg-cream/95">
        <div className="mx-auto flex max-w-3xl items-center gap-3 px-4 py-4">
          <Link to="/profile" className="rounded-full border border-border bg-white p-2">
            <ArrowLeft className="h-5 w-5 text-ink" />
          </Link>
          <div className="flex-1">
            <p className="font-script text-primary">Your places</p>
            <h1 className="font-display text-xl font-bold text-ink">Delivery addresses</h1>
          </div>
          <button type="button" onClick={beginCreate} className="btn-primary px-4 py-2 text-sm">
            <Plus className="h-4 w-4" /> Add
          </button>
        </div>
      </header>

      <main className="mx-auto max-w-3xl px-4 pt-6">
        <div className="space-y-3">
          {addresses.map((address) => {
            const ready = isDeliveryReadyAddress(address);
            return (
              <article key={address.id} className="rounded-2xl border border-border bg-card p-5 shadow-sm">
                <div className="flex items-start gap-3">
                  <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                    <MapPin className="h-5 w-5" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="font-display text-lg font-bold text-ink">{address.addressLabel}</h2>
                      {address.isDefault && (
                        <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-bold text-primary">
                          <Check className="mr-1 inline h-3 w-3" />DEFAULT
                        </span>
                      )}
                      {!ready && (
                        <span className="rounded-full bg-amber-100 px-2 py-0.5 text-[10px] font-bold text-amber-800">UPDATE REQUIRED</span>
                      )}
                    </div>
                    <p className="mt-1 text-sm font-semibold text-ink">{recipientLine(address)}</p>
                    <p className="mt-1 text-sm text-muted-foreground">{addressLine(address)}</p>
                    {!ready && (
                      <p className="mt-3 flex items-start gap-2 rounded-xl bg-amber-50 p-3 text-xs leading-5 text-amber-900">
                        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                        This older saved address needs missing delivery details before checkout.
                      </p>
                    )}
                  </div>
                  <div className="flex gap-1">
                    <button type="button" onClick={() => beginEdit(address)} className="rounded-full p-2 text-muted-foreground hover:bg-secondary hover:text-primary" aria-label="Edit address"><Pencil className="h-4 w-4" /></button>
                    <button type="button" disabled={busy} onClick={() => void remove(address)} className="rounded-full p-2 text-muted-foreground hover:bg-secondary hover:text-destructive" aria-label="Delete address"><Trash2 className="h-4 w-4" /></button>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
        <p role="status" className="mt-4 rounded-xl bg-secondary p-3 text-sm text-muted-foreground">{message}</p>
      </main>

      {open && (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/50 md:items-center">
          <div className="max-h-[92vh] w-full max-w-lg overflow-y-auto rounded-t-3xl bg-cream p-6 md:rounded-3xl">
            <div className="flex items-center justify-between">
              <h2 className="font-display text-xl font-bold text-ink">{editingId ? "Edit address" : "Add address"}</h2>
              <button type="button" onClick={() => setOpen(false)} className="rounded-full p-2"><X className="h-5 w-5" /></button>
            </div>

            <button
              type="button"
              onClick={captureLocation}
              disabled={locating || busy}
              className="mt-5 flex w-full items-center justify-center gap-2 rounded-xl border border-primary bg-primary/5 p-3 text-sm font-bold text-primary disabled:opacity-50"
            >
              {locating ? <Loader2 className="h-4 w-4 animate-spin" /> : <Crosshair className="h-4 w-4" />}
              {locating ? "Detecting your address…" : "Use my current location"}
            </button>
            <p className="mt-2 text-center text-[11px] leading-4 text-muted-foreground">
              Craves fills the available house/building, street, area, district, city, state and pincode automatically. You can edit every written field.
            </p>

            <div className="mt-5 grid gap-3 sm:grid-cols-2">
              <label className="text-xs font-semibold text-ink">Label<select value={draft.addressLabel} onChange={(event) => update("addressLabel", event.target.value as AddressLabel)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm"><option value="HOME">Home</option><option value="WORK">Work</option><option value="OTHER">Other</option></select></label>
              <label className="text-xs font-semibold text-ink">Recipient<input value={draft.recipientName} onChange={(event) => update("recipientName", event.target.value)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm" /></label>
              <label className="text-xs font-semibold text-ink">Phone<input value={draft.contactPhoneNumber} onChange={(event) => update("contactPhoneNumber", event.target.value)} placeholder="+919876543210" className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm" /></label>
              <label className="text-xs font-semibold text-ink">Pincode<input value={draft.postalCode} onChange={(event) => update("postalCode", event.target.value)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm" /></label>
              <label className="text-xs font-semibold text-ink sm:col-span-2">Flat / House / Building<input value={draft.addressLine1} onChange={(event) => update("addressLine1", event.target.value)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm" /></label>
              <label className="text-xs font-semibold text-ink sm:col-span-2">Street / Road<input value={draft.addressLine2 ?? ""} onChange={(event) => update("addressLine2", event.target.value || null)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm" /></label>
              <label className="text-xs font-semibold text-ink">Area<input value={draft.areaName} onChange={(event) => update("areaName", event.target.value)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm" /></label>
              <label className="text-xs font-semibold text-ink">District<input value={draft.districtName} onChange={(event) => update("districtName", event.target.value)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm" /></label>
              <label className="text-xs font-semibold text-ink">City<input value={draft.city} onChange={(event) => update("city", event.target.value)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm" /></label>
              <label className="text-xs font-semibold text-ink">State<input value={draft.state} onChange={(event) => update("state", event.target.value)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm" /></label>
              <label className="text-xs font-semibold text-ink sm:col-span-2">Landmark (optional)<input value={draft.landmark ?? ""} onChange={(event) => update("landmark", event.target.value || null)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm" /></label>
              <label className="flex items-center gap-2 text-sm font-semibold text-ink sm:col-span-2"><input type="checkbox" checked={draft.isDefault} onChange={(event) => update("isDefault", event.target.checked)} /> Make this my default address</label>
            </div>

            <p className="mt-3 text-xs leading-5 text-muted-foreground">
              The exact map point is stored securely in the background for discovery and delivery. Latitude and longitude are intentionally hidden.
            </p>
            <button type="button" disabled={busy || locating} onClick={() => void save()} className="btn-primary mt-6 w-full justify-center disabled:opacity-50">
              {busy ? "Saving…" : editingId ? "Update address" : "Save address"}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
