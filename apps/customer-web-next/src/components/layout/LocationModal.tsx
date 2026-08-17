import { useState } from "react";
import { X, MapPin, LocateFixed, Loader2 } from "lucide-react";
import { saveAddress, type CravesAddress } from "@/services/auth/cravesAuth";
import { reverseGeocodeCurrentLocation } from "@/services/location/reverseGeocode";

interface LocationModalProps {
  open: boolean;
  onClose: () => void;
  onSaved?: (addr: CravesAddress) => void;
}

type Notice = {
  tone: "success" | "error";
  text: string;
} | null;

export function LocationModal({ open, onClose, onSaved }: LocationModalProps) {
  const [form, setForm] = useState<CravesAddress>({
    hno: "",
    street: "",
    city: "",
    mandal: "",
    district: "",
    pincode: "",
  });
  const [locating, setLocating] = useState(false);
  const [notice, setNotice] = useState<Notice>(null);

  if (!open) return null;

  const set = (k: keyof CravesAddress) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  const useLiveLocation = () => {
    setNotice(null);
    if (!("geolocation" in navigator)) {
      setNotice({ tone: "error", text: "Geolocation is not supported by this browser." });
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const latitude = Number(pos.coords.latitude.toFixed(7));
        const longitude = Number(pos.coords.longitude.toFixed(7));
        try {
          const detected = await reverseGeocodeCurrentLocation(latitude, longitude);
          const next: CravesAddress = {
            ...form,
            hno: detected.houseNumber || detected.formattedAddress,
            street: detected.street || "",
            city: detected.city || "",
            mandal: detected.area || detected.city || "",
            district: detected.district || detected.city || "",
            pincode: detected.postalCode || "",
            lat: latitude,
            lng: longitude,
          };
          setForm(next);
          setNotice({
            tone: "success",
            text: detected.preciseHouseNumber
              ? "Address detected. Review the house/building details and confirm."
              : "Location detected. Please confirm or correct the flat/house/building before continuing.",
          });
        } catch (locationError) {
          setForm((current) => ({ ...current, lat: latitude, lng: longitude }));
          setNotice({
            tone: "error",
            text: locationError instanceof Error
              ? locationError.message
              : "Could not identify the written address for this location.",
          });
        } finally {
          setLocating(false);
        }
      },
      (geoError) => {
        setLocating(false);
        setNotice({
          tone: "error",
          text: geoError.message || "Could not access your location.",
        });
      },
      { enableHighAccuracy: true, timeout: 12_000, maximumAge: 30_000 },
    );
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (
      !form.hno
      || !form.city
      || !form.mandal
      || !form.district
      || !form.pincode
      || form.lat == null
      || form.lng == null
    ) {
      setNotice({
        tone: "error",
        text: "Use current location and confirm the complete delivery address.",
      });
      return;
    }
    saveAddress(form);
    onSaved?.(form);
    onClose();
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 px-4"
      onClick={onClose}
    >
      <div
        className="relative w-full max-w-md rounded-2xl bg-cream shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-border px-6 py-4">
          <h2 className="flex items-center gap-2 text-xl font-bold text-ink">
            <MapPin className="h-5 w-5 text-primary" /> Delivery Location
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full p-1 text-ink hover:bg-black/5"
            aria-label="Close"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <form onSubmit={handleSubmit} className="space-y-3 px-6 py-6">
          <button
            type="button"
            onClick={useLiveLocation}
            disabled={locating}
            className="flex w-full items-center justify-center gap-2 rounded-lg border border-primary/40 bg-primary/10 px-3 py-3 text-sm font-semibold text-primary transition-colors hover:bg-primary/15 disabled:opacity-60"
          >
            {locating ? <Loader2 className="h-4 w-4 animate-spin" /> : <LocateFixed className="h-4 w-4" />}
            {locating ? "Detecting your address…" : "Use my current location"}
          </button>
          <p className="text-center text-[11px] leading-4 text-muted-foreground">
            Craves will automatically fill the available house/building, street, area, district, city and pincode. You can correct any field before confirming.
          </p>
          <div className="relative flex items-center gap-3 py-1 text-xs text-muted-foreground">
            <span className="h-px flex-1 bg-border" /> ADDRESS DETAILS <span className="h-px flex-1 bg-border" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Flat / House / Building" value={form.hno} onChange={set("hno")} required />
            <Field label="Street / Road" value={form.street ?? ""} onChange={set("street")} />
            <Field label="Area" value={form.mandal} onChange={set("mandal")} required />
            <Field label="City" value={form.city} onChange={set("city")} required />
            <Field label="District" value={form.district} onChange={set("district")} required />
            <Field label="Pincode" value={form.pincode ?? ""} onChange={set("pincode")} inputMode="numeric" maxLength={6} required />
          </div>
          {notice && (
            <p
              role="status"
              className={
                notice.tone === "success"
                  ? "rounded-md bg-emerald-50 px-3 py-2 text-xs font-medium text-emerald-800"
                  : "rounded-md bg-destructive/10 px-3 py-2 text-xs font-medium text-destructive"
              }
            >
              {notice.text}
            </p>
          )}
          <button type="submit" className="btn-primary w-full justify-center rounded-lg py-3 text-base">
            Confirm delivery location
          </button>
        </form>
      </div>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  required,
  inputMode,
  maxLength,
}: {
  label: string;
  value: string;
  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  required?: boolean;
  inputMode?: "numeric" | "text";
  maxLength?: number;
}) {
  return (
    <label className="text-xs font-medium text-ink">
      {label}
      {required && <span className="text-primary"> *</span>}
      <input
        type="text"
        value={value}
        onChange={onChange}
        inputMode={inputMode}
        maxLength={maxLength}
        className="mt-1 w-full rounded-lg border border-border bg-white px-3 py-2.5 text-sm text-ink outline-none placeholder:text-muted-foreground focus:border-primary"
      />
    </label>
  );
}
