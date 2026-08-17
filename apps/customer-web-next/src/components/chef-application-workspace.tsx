"use client";

import Link from "next/link";
import { type FormEvent, useEffect, useState } from "react";
import type { CustomerAddress } from "@/lib/address-contract";
import { selectActiveDeliveryAddress } from "@/lib/address-selection";
import type {
  ChefApplication,
  ChefDocumentType,
} from "@/lib/chef-application-contract";
import type { CustomerProfile } from "@/lib/profile-contract";
import { reverseGeocodeCurrentLocation } from "@/services/location/reverseGeocode";

type FormState = {
  email: string;
  firstName: string;
  lastName: string;
  addressLine1: string;
  addressLine2: string;
  landmark: string;
  city: string;
  state: string;
  postalCode: string;
  latitude: string;
  longitude: string;
};

const EMPTY: FormState = {
  email: "",
  firstName: "",
  lastName: "",
  addressLine1: "",
  addressLine2: "",
  landmark: "",
  city: "",
  state: "",
  postalCode: "",
  latitude: "",
  longitude: "",
};

function fromApplication(application: ChefApplication): FormState {
  return {
    email: application.email ?? "",
    firstName: application.firstName ?? "",
    lastName: application.lastName ?? "",
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

function prefillNewApplication(
  application: ChefApplication,
  profile: CustomerProfile | null,
  addresses: CustomerAddress[],
): FormState {
  const form = fromApplication(application);
  const address = selectActiveDeliveryAddress(addresses);
  return {
    ...form,
    email: form.email || profile?.email || "",
    firstName: form.firstName || profile?.firstName || "",
    lastName: form.lastName || profile?.lastName || "",
    addressLine1: form.addressLine1 || address?.addressLine1 || "",
    addressLine2: form.addressLine2 || address?.addressLine2 || "",
    landmark: form.landmark || address?.landmark || "",
    city: form.city || address?.city || "",
    state: form.state || address?.state || "",
    postalCode: form.postalCode || address?.postalCode || "",
    latitude:
      form.latitude ||
      (typeof address?.latitude === "number" ? String(address.latitude) : ""),
    longitude:
      form.longitude ||
      (typeof address?.longitude === "number" ? String(address.longitude) : ""),
  };
}

function responseMessage(
  body: { code?: unknown; message?: unknown } | null,
  fallback: string,
): string {
  return typeof body?.message === "string" && body.message.trim()
    ? body.message
    : fallback;
}

export function ChefApplicationWorkspace() {
  const [application, setApplication] = useState<ChefApplication | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY);
  const [message, setMessage] = useState("Loading your chef application…");
  const [busy, setBusy] = useState(false);
  const [locating, setLocating] = useState(false);
  const [proofType, setProofType] = useState<ChefDocumentType>("AADHAAR_CARD");
  const [proofFile, setProofFile] = useState<File | null>(null);

  async function load() {
    const [applicationResponse, profileResponse, addressesResponse] =
      await Promise.all([
        fetch("/api/chef/application", { cache: "no-store" }),
        fetch("/api/customer/profile", { cache: "no-store" }),
        fetch("/api/customer/addresses", { cache: "no-store" }),
      ]);
    const applicationBody = (await applicationResponse
      .json()
      .catch(() => null)) as ChefApplication | null;
    if (!applicationResponse.ok || !applicationBody) {
      throw new Error(
        applicationResponse.status === 401
          ? "Sign in to manage your chef application."
          : "Chef application is temporarily unavailable.",
      );
    }

    const profile = profileResponse.ok
      ? ((await profileResponse.json().catch(() => null)) as CustomerProfile | null)
      : null;
    const addresses = addressesResponse.ok
      ? ((await addressesResponse.json().catch(() => [])) as CustomerAddress[])
      : [];
    const nextForm =
      applicationBody.status === "NOT_SUBMITTED"
        ? prefillNewApplication(applicationBody, profile, addresses)
        : fromApplication(applicationBody);
    setApplication(applicationBody);
    setForm(nextForm);
    setMessage(
      applicationBody.status === "NOT_SUBMITTED"
        ? "Your saved Craves profile and default address have been prefilled. Review them before submitting."
        : "",
    );
  }

  useEffect(() => {
    void load().catch((error) =>
      setMessage(
        error instanceof Error
          ? error.message
          : "Chef application is temporarily unavailable.",
      ),
    );
  }, []);

  function field<K extends keyof FormState>(name: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  function useCurrentLocation() {
    if (!navigator.geolocation) {
      setMessage("This browser cannot provide a location.");
      return;
    }
    setLocating(true);
    setMessage("Detecting your current address…");
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        const latitude = Number(position.coords.latitude.toFixed(7));
        const longitude = Number(position.coords.longitude.toFixed(7));
        try {
          const detected = await reverseGeocodeCurrentLocation(latitude, longitude);
          const areaDetails = [detected.street, detected.area, detected.district]
            .filter(Boolean)
            .join(", ");
          setForm((current) => ({
            ...current,
            addressLine1: detected.houseNumber || detected.formattedAddress,
            addressLine2: detected.houseNumber ? areaDetails : current.addressLine2,
            city: detected.city || current.city,
            state: detected.state || current.state,
            postalCode: detected.postalCode || current.postalCode,
            latitude: String(latitude),
            longitude: String(longitude),
          }));
          setMessage(
            detected.preciseHouseNumber
              ? "Address detected and filled automatically. Review the written details before submitting."
              : "Location detected and the available address was filled. Please confirm or correct the flat/house/building details.",
          );
        } catch (error) {
          setForm((current) => ({
            ...current,
            latitude: String(latitude),
            longitude: String(longitude),
          }));
          setMessage(
            error instanceof Error
              ? `${error.message} The map point was captured; complete the written address manually.`
              : "The map point was captured but the written address could not be identified.",
          );
        } finally {
          setLocating(false);
        }
      },
      () => {
        setLocating(false);
        setMessage("Location permission was not granted. Your saved address remains unchanged.");
      },
      { enableHighAccuracy: true, timeout: 12_000, maximumAge: 30_000 },
    );
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const updating = application?.status === "PENDING";
    setBusy(true);
    setMessage(
      updating
        ? "Updating your pending chef application…"
        : "Submitting your chef application…",
    );
    try {
      const response = await fetch("/api/chef/application", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...form,
          addressLine2: form.addressLine2 || null,
          landmark: form.landmark || null,
          postalCode: form.postalCode || null,
          latitude: form.latitude === "" ? null : Number(form.latitude),
          longitude: form.longitude === "" ? null : Number(form.longitude),
        }),
      });
      const body = (await response.json().catch(() => null)) as {
        code?: unknown;
        message?: unknown;
      } | null;
      if (!response.ok) {
        throw new Error(
          responseMessage(
            body,
            response.status === 400
              ? "Complete all required fields using valid values."
              : "Application submission failed.",
          ),
        );
      }
      const nextApplication = body as unknown as ChefApplication;
      setApplication(nextApplication);
      setForm(fromApplication(nextApplication));
      setMessage(
        updating
          ? "Pending application updated. Craves admin review remains authoritative."
          : "Chef application submitted. Upload the requested proofs and wait for Craves admin review.",
      );
    } catch (error) {
      setMessage(
        error instanceof Error
          ? error.message
          : "Application submission failed.",
      );
    } finally {
      setBusy(false);
    }
  }

  async function uploadProof() {
    if (!proofFile) {
      setMessage("Choose a PDF, JPG or PNG proof file first.");
      return;
    }
    setBusy(true);
    setMessage("Uploading proof file…");
    try {
      const data = new FormData();
      data.set("documentType", proofType);
      data.set("file", proofFile);
      const response = await fetch("/api/chef/application/proof-files", {
        method: "POST",
        body: data,
      });
      const body = (await response.json().catch(() => null)) as {
        message?: unknown;
      } | null;
      if (!response.ok) {
        throw new Error(
          responseMessage(
            body,
            "Proof upload failed. Use a PDF, JPG or PNG file under 10 MB.",
          ),
        );
      }
      setProofFile(null);
      await load();
      setMessage("Proof file uploaded for admin review.");
    } catch (error) {
      setMessage(
        error instanceof Error ? error.message : "Proof upload failed.",
      );
    } finally {
      setBusy(false);
    }
  }

  const locked = application?.status === "APPROVED";
  const fields: Array<
    [keyof FormState, string, "text" | "email", boolean]
  > = [
    ["email", "Email", "email", true],
    ["firstName", "First name", "text", true],
    ["lastName", "Last name", "text", true],
    ["addressLine1", "Flat / House / Building", "text", true],
    ["addressLine2", "Street / Area / District", "text", false],
    ["landmark", "Landmark", "text", false],
    ["city", "City", "text", true],
    ["state", "State", "text", true],
    ["postalCode", "Pincode", "text", false],
  ];

  return (
    <div className="space-y-6">
      <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950 sm:p-8">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.2em] text-[#6930CA]">
              Application status
            </p>
            <h2 className="mt-2 text-3xl font-bold">
              {application?.status?.replaceAll("_", " ") ?? "Loading"}
            </h2>
          </div>
          {application?.reviewedAt && (
            <span className="text-sm text-slate-600">
              Reviewed {new Date(application.reviewedAt).toLocaleString("en-IN")}
            </span>
          )}
        </div>
        {application?.status === "REJECTED" && application.rejectionReason && (
          <p className="mt-4 rounded-2xl bg-red-50 p-4 text-sm text-red-800">
            Review note: {application.rejectionReason}
          </p>
        )}
        {application?.status === "PENDING" && (
          <p className="mt-4 rounded-2xl bg-amber-50 p-4 text-sm text-amber-900">
            Your application is waiting for admin review. You can still correct
            the details below or replace proof files until it is approved.
          </p>
        )}
        {application?.status === "APPROVED" && (
          <Link
            href="/chef"
            className="mt-4 inline-flex rounded-full bg-[#6930CA] px-5 py-3 text-sm font-bold text-white"
          >
            Continue to chef mode
          </Link>
        )}
        <p role="status" className="mt-4 text-sm text-slate-600">{message}</p>
      </section>

      <form
        onSubmit={submit}
        className="rounded-[30px] bg-white p-6 text-slate-950 sm:p-8"
      >
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-2xl font-bold">Chef details</h2>
          <button
            type="button"
            disabled={locked || busy || locating}
            onClick={useCurrentLocation}
            className="rounded-full border border-[#6930CA] px-4 py-2 text-sm font-bold text-[#6930CA] disabled:opacity-50"
          >
            {locating ? "Detecting address…" : "Use current location"}
          </button>
        </div>
        <p className="mt-3 text-xs leading-5 text-slate-500">
          Craves stores the precise map point in the background. Chefs never need to enter latitude or longitude.
        </p>
        <div className="mt-5 grid gap-4 md:grid-cols-2">
          {fields.map(([name, label, inputType, required]) => (
            <label key={name} className="text-sm font-semibold">
              {label}{required ? " *" : ""}
              <input
                type={inputType}
                required={required}
                disabled={locked || busy}
                value={form[name]}
                onChange={(event) => field(name, event.target.value)}
                className="mt-2 w-full rounded-2xl border border-slate-300 px-4 py-3 disabled:bg-slate-100"
              />
            </label>
          ))}
        </div>
        <button
          type="submit"
          disabled={locked || busy || locating}
          className="mt-6 rounded-full bg-[#6930CA] px-6 py-3 font-bold text-white disabled:opacity-50"
        >
          {application?.status === "PENDING"
            ? "Update pending application"
            : application?.status === "REJECTED"
              ? "Resubmit application"
              : application?.status === "APPROVED"
                ? "Application approved"
                : "Submit application"}
        </button>
      </form>

      <section className="rounded-[30px] bg-[#FFF8EC] p-6 text-slate-950 sm:p-8">
        <h2 className="text-2xl font-bold">Proof files</h2>
        <p className="mt-2 text-sm text-slate-600">
          Upload only the proof types supported by the current backend. File
          contents are never returned to the browser after upload.
        </p>
        <div className="mt-5 grid gap-4 md:grid-cols-[220px_1fr_auto] md:items-end">
          <label className="text-sm font-semibold">
            Document type
            <select
              value={proofType}
              onChange={(event) =>
                setProofType(event.target.value as ChefDocumentType)
              }
              className="mt-2 w-full rounded-2xl border border-slate-300 bg-white px-4 py-3"
            >
              <option value="AADHAAR_CARD">Aadhaar card</option>
              <option value="PAN_CARD">PAN card</option>
            </select>
          </label>
          <label className="text-sm font-semibold">
            File
            <input
              type="file"
              accept="application/pdf,image/jpeg,image/png"
              onChange={(event) => setProofFile(event.target.files?.[0] ?? null)}
              className="mt-2 block w-full rounded-2xl border border-slate-300 bg-white px-4 py-3"
            />
          </label>
          <button
            type="button"
            disabled={busy || !application?.id || locked}
            onClick={() => void uploadProof()}
            className="rounded-full bg-[#6930CA] px-6 py-3 font-bold text-white disabled:opacity-50"
          >
            Upload
          </button>
        </div>
        <div className="mt-6 space-y-3">
          {application?.documents.map((document) => (
            <div key={document.id} className="rounded-2xl bg-white p-4">
              <div className="flex flex-wrap justify-between gap-3">
                <strong>{document.documentType.replaceAll("_", " ")}</strong>
                <span className="text-sm text-slate-600">{document.status}</span>
              </div>
              <p className="mt-1 text-sm text-slate-600">
                {document.originalFileName} · {(document.fileSizeBytes / 1024).toFixed(1)} KB
              </p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
