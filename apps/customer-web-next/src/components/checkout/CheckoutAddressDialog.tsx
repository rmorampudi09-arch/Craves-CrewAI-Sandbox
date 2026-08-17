"use client";

import { useCallback, useEffect, useState } from "react";
import {
  Check,
  Crosshair,
  LoaderCircle,
  MapPin,
  Plus,
  X,
} from "lucide-react";
import {
  isDeliveryReadyAddress,
  parseAddressInput,
  parseCustomerAddress,
  parseCustomerAddresses,
  type AddressLabel,
  type CustomerAddress,
  type CustomerAddressInput,
} from "@/lib/address-contract";
import { reverseGeocodeCurrentLocation } from "@/services/location/reverseGeocode";

type AddressDraft = Omit<CustomerAddressInput, "latitude" | "longitude"> & {
  latitude: string;
  longitude: string;
};

const blankAddress: AddressDraft = {
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

function fullAddress(address: CustomerAddress): string {
  return [
    address.addressLine1,
    address.addressLine2,
    address.landmark,
    address.areaName,
    address.districtName,
    address.city,
    address.state,
    address.postalCode,
  ]
    .filter(Boolean)
    .join(", ");
}

export function CheckoutAddressDialog({
  open,
  selectedId,
  onClose,
  onSelect,
  onAddressesChange,
}: {
  open: boolean;
  selectedId: string;
  onClose(): void;
  onSelect(id: string): void;
  onAddressesChange(addresses: CustomerAddress[]): void;
}) {
  const [addresses, setAddresses] = useState<CustomerAddress[]>([]);
  const [draft, setDraft] = useState<AddressDraft>(blankAddress);
  const [showForm, setShowForm] = useState(false);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [locating, setLocating] = useState(false);
  const [message, setMessage] = useState("");

  const loadAddresses = useCallback(async () => {
    setLoading(true);
    setMessage("");
    try {
      const response = await fetch("/api/customer/addresses", {
        cache: "no-store",
        credentials: "same-origin",
      });
      const body = await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(
          body && typeof body === "object" && "message" in body && typeof body.message === "string"
            ? body.message
            : "Saved addresses could not be loaded.",
        );
      }
      const parsed = parseCustomerAddresses(body);
      if (!parsed) throw new Error("Craves returned an invalid address response.");
      setAddresses(parsed);
      onAddressesChange(parsed.filter(isDeliveryReadyAddress));

      const current = parsed.find(
        (address) => address.id === selectedId && isDeliveryReadyAddress(address),
      );
      const preferred =
        current
        ?? parsed.find((address) => address.isDefault && isDeliveryReadyAddress(address))
        ?? parsed.find(isDeliveryReadyAddress);
      if (preferred && preferred.id !== selectedId) onSelect(preferred.id);
      setMessage(
        parsed.length === 0
          ? "No saved addresses yet. Add one below."
          : `${parsed.length} saved address${parsed.length === 1 ? "" : "es"} loaded.`,
      );
    } catch (error) {
      setMessage(
        error instanceof Error ? error.message : "Saved addresses could not be loaded.",
      );
    } finally {
      setLoading(false);
    }
  }, [onAddressesChange, onSelect, selectedId]);

  useEffect(() => {
    if (!open) return;
    setShowForm(false);
    setDraft(blankAddress);
    void loadAddresses();
  }, [loadAddresses, open]);

  function update<K extends keyof AddressDraft>(key: K, value: AddressDraft[K]) {
    setDraft((current) => ({ ...current, [key]: value }));
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
              ? "Current address detected and filled automatically. Confirm the flat/house details before saving."
              : "Location detected. Craves filled the available address; please confirm or correct the flat/house/building.",
          );
        } catch (error) {
          setDraft((current) => ({
            ...current,
            latitude: String(latitude),
            longitude: String(longitude),
          }));
          setMessage(
            error instanceof Error
              ? `${error.message} You can enter the written address manually.`
              : "Location captured but the written address could not be identified.",
          );
        } finally {
          setLocating(false);
        }
      },
      () => {
        setLocating(false);
        setMessage("Location permission was not granted. You can enter the address manually.");
      },
      { enableHighAccuracy: true, timeout: 12_000, maximumAge: 30_000 },
    );
  }

  async function saveAddress() {
    const input = parseAddressInput({
      ...draft,
      addressLine2: draft.addressLine2?.trim() || null,
      landmark: draft.landmark?.trim() || null,
      latitude: draft.latitude.trim(),
      longitude: draft.longitude.trim(),
    });
    if (!input) {
      setMessage(
        "Confirm the recipient, phone and complete delivery address, then use current location so Craves can map the drop-off point.",
      );
      return;
    }

    setBusy(true);
    setMessage("Saving address…");
    try {
      const response = await fetch("/api/customer/addresses", {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(input),
      });
      const body = await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(
          body && typeof body === "object" && "message" in body && typeof body.message === "string"
            ? body.message
            : "Address could not be saved.",
        );
      }
      const created = parseCustomerAddress(body);
      if (!created) throw new Error("Craves returned an invalid saved address response.");
      setShowForm(false);
      setDraft(blankAddress);
      await loadAddresses();
      if (isDeliveryReadyAddress(created)) onSelect(created.id);
      setMessage("Address saved and selected for this checkout.");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Address could not be saved.");
    } finally {
      setBusy(false);
    }
  }

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-[70] flex items-end justify-center bg-black/55 p-0 md:items-center md:p-6"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-labelledby="checkout-address-dialog-title"
        className="max-h-[94vh] w-full max-w-2xl overflow-y-auto rounded-t-3xl border border-border bg-white p-5 shadow-[var(--shadow-pop)] md:rounded-3xl md:p-6"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="craves-overline text-primary">Delivery address</p>
            <h2 id="checkout-address-dialog-title" className="mt-1 font-display text-2xl font-bold text-ink">
              Where should we deliver?
            </h2>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">
              Use a saved address or let Craves detect and fill your current address automatically.
            </p>
          </div>
          <button type="button" onClick={onClose} className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border" aria-label="Close address manager">
            <X className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        {!showForm && (
          <>
            <div className="mt-5 space-y-3" aria-live="polite">
              {loading ? (
                <div className="flex min-h-28 items-center justify-center rounded-2xl border border-border bg-grey-50">
                  <LoaderCircle className="h-6 w-6 animate-spin text-primary" aria-hidden="true" />
                </div>
              ) : (
                addresses.map((address) => {
                  const ready = isDeliveryReadyAddress(address);
                  const selected = selectedId === address.id;
                  return (
                    <button
                      key={address.id}
                      type="button"
                      disabled={!ready}
                      onClick={() => {
                        onSelect(address.id);
                        onClose();
                      }}
                      className={`flex w-full items-start gap-3 rounded-2xl border p-4 text-left ${selected ? "ring-2 ring-[#F62E18]" : ""} disabled:cursor-not-allowed disabled:opacity-55`}
                    >
                      <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/25">
                        {selected ? <Check className="h-5 w-5" aria-hidden="true" /> : <MapPin className="h-5 w-5" aria-hidden="true" />}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block font-display text-base font-bold">
                          {address.addressLabel}{address.isDefault ? " · Default" : ""}
                        </span>
                        <span className="mt-1 block text-sm font-semibold">
                          {[address.recipientName, address.contactPhoneNumber].filter(Boolean).join(" · ")}
                        </span>
                        <span className="mt-1 block text-sm leading-5">{fullAddress(address)}</span>
                        {!ready && <span className="mt-2 block text-xs font-bold">This older address needs missing details before checkout.</span>}
                      </span>
                    </button>
                  );
                })
              )}
            </div>
            <button
              type="button"
              onClick={() => {
                setDraft(blankAddress);
                setShowForm(true);
                setMessage("");
              }}
              className="mt-5 flex min-h-12 w-full items-center justify-center gap-2 rounded-xl border px-4"
            >
              <Plus className="h-4 w-4" aria-hidden="true" /> Add new address
            </button>
          </>
        )}

        {showForm && (
          <div className="mt-5">
            <div className="flex items-center justify-between gap-3">
              <h3 className="font-display text-xl font-bold text-ink">Add delivery address</h3>
              <button type="button" onClick={() => setShowForm(false)} className="min-h-11 rounded-lg border px-4 text-sm">Saved addresses</button>
            </div>

            <button
              type="button"
              onClick={captureLocation}
              disabled={locating || busy}
              className="mt-4 flex min-h-14 w-full items-center justify-center gap-2 rounded-xl border border-[#F62E18]/30 bg-[#F62E18]/5 p-3 text-sm font-bold text-[#F62E18] disabled:opacity-50"
            >
              {locating ? <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Crosshair className="h-4 w-4" aria-hidden="true" />}
              {locating ? "Detecting your address…" : "Use my current location"}
            </button>

            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <label className="text-xs font-semibold text-ink">Label<select value={draft.addressLabel} onChange={(event) => update("addressLabel", event.target.value as AddressLabel)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm text-ink outline-none focus:border-[#F62E18]"><option value="HOME">Home</option><option value="WORK">Work</option><option value="OTHER">Other</option></select></label>
              <label className="text-xs font-semibold text-ink">Recipient<input value={draft.recipientName} onChange={(event) => update("recipientName", event.target.value)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm text-ink outline-none focus:border-[#F62E18]" /></label>
              <label className="text-xs font-semibold text-ink">Phone<input value={draft.contactPhoneNumber} onChange={(event) => update("contactPhoneNumber", event.target.value)} placeholder="+919876543210" className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm text-ink outline-none focus:border-[#F62E18]" /></label>
              <label className="text-xs font-semibold text-ink">Pincode<input value={draft.postalCode} onChange={(event) => update("postalCode", event.target.value)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm text-ink outline-none focus:border-[#F62E18]" /></label>
              <label className="text-xs font-semibold text-ink sm:col-span-2">Flat / House / Building<input value={draft.addressLine1} onChange={(event) => update("addressLine1", event.target.value)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm text-ink outline-none focus:border-[#F62E18]" /></label>
              <label className="text-xs font-semibold text-ink sm:col-span-2">Street / Road<input value={draft.addressLine2 ?? ""} onChange={(event) => update("addressLine2", event.target.value || null)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm text-ink outline-none focus:border-[#F62E18]" /></label>
              <label className="text-xs font-semibold text-ink">Area<input value={draft.areaName} onChange={(event) => update("areaName", event.target.value)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm text-ink outline-none focus:border-[#F62E18]" /></label>
              <label className="text-xs font-semibold text-ink">District<input value={draft.districtName} onChange={(event) => update("districtName", event.target.value)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm text-ink outline-none focus:border-[#F62E18]" /></label>
              <label className="text-xs font-semibold text-ink">City<input value={draft.city} onChange={(event) => update("city", event.target.value)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm text-ink outline-none focus:border-[#F62E18]" /></label>
              <label className="text-xs font-semibold text-ink">State<input value={draft.state} onChange={(event) => update("state", event.target.value)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm text-ink outline-none focus:border-[#F62E18]" /></label>
              <label className="text-xs font-semibold text-ink sm:col-span-2">Landmark (optional)<input value={draft.landmark ?? ""} onChange={(event) => update("landmark", event.target.value || null)} className="mt-1 w-full rounded-xl border border-border bg-white p-3 text-sm text-ink outline-none focus:border-[#F62E18]" /></label>
              <label className="flex items-center gap-2 text-sm font-semibold text-ink sm:col-span-2"><input type="checkbox" checked={draft.isDefault} onChange={(event) => update("isDefault", event.target.checked)} />Make this my default address</label>
            </div>

            <p className="mt-3 text-xs leading-5 text-muted-foreground">
              Craves keeps the precise map point in the background; latitude and longitude are intentionally hidden from customers.
            </p>

            <button type="button" disabled={busy || locating} onClick={() => void saveAddress()} className="btn-primary mt-5 min-h-12 w-full disabled:opacity-50">
              {busy && <LoaderCircle className="h-4 w-4 animate-spin" aria-hidden="true" />}
              {busy ? "Saving…" : "Save and use this address"}
            </button>
          </div>
        )}

        {message && <p role="status" className="mt-4 rounded-xl border border-border bg-grey-50 p-3 text-sm text-muted-foreground">{message}</p>}
      </section>
    </div>
  );
}
