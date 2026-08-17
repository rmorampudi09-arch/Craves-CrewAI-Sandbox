"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  CheckCircle2,
  Clock3,
  FileCheck2,
  ImageIcon,
  MapPinned,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  Store,
  Utensils,
} from "lucide-react";
import {
  parseChefApplication,
  type ChefApplication,
} from "@/lib/chef-application-contract";
import { parseChefKitchen } from "@/lib/chef-kitchen-contract";
import type { ChefKitchen } from "@/lib/chef-kitchen-types";
import {
  parseChefMenuItems,
  type ChefMenuItem,
} from "@/lib/chef-menu-contract";

type LoadState = "loading" | "ready" | "error";

function responseMessage(value: unknown, fallback: string): string {
  return value &&
    typeof value === "object" &&
    "message" in value &&
    typeof value.message === "string"
    ? value.message
    : fallback;
}

function statusTone(ready: boolean): string {
  return ready
    ? "border-success/20 bg-success/5 text-success"
    : "border-warning/20 bg-warning/5 text-warning";
}

export function ChefOperationsWorkspace() {
  const [application, setApplication] = useState<ChefApplication | null>(null);
  const [kitchen, setKitchen] = useState<ChefKitchen | null>(null);
  const [menu, setMenu] = useState<ChefMenuItem[]>([]);
  const [state, setState] = useState<LoadState>("loading");
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState("");
  const [lastUpdatedAt, setLastUpdatedAt] = useState<Date | null>(null);

  const load = useCallback(async (background = false) => {
    if (background) setRefreshing(true);
    else setState("loading");
    setError("");
    try {
      const [applicationResponse, kitchenResponse, menuResponse] =
        await Promise.all([
          fetch("/api/chef/application", {
            cache: "no-store",
            credentials: "same-origin",
          }),
          fetch("/api/chef/kitchen", {
            cache: "no-store",
            credentials: "same-origin",
          }),
          fetch("/api/chef/menu", {
            cache: "no-store",
            credentials: "same-origin",
          }),
        ]);

      const [applicationRaw, kitchenRaw, menuRaw] = await Promise.all([
        applicationResponse.json().catch(() => null),
        kitchenResponse.json().catch(() => null),
        menuResponse.json().catch(() => null),
      ]);

      if (!applicationResponse.ok) {
        throw new Error(
          responseMessage(
            applicationRaw,
            "Chef application status could not be loaded.",
          ),
        );
      }
      if (!kitchenResponse.ok) {
        throw new Error(
          responseMessage(kitchenRaw, "Kitchen operations could not be loaded."),
        );
      }
      if (!menuResponse.ok) {
        throw new Error(
          responseMessage(menuRaw, "Menu operations could not be loaded."),
        );
      }

      const parsedApplication = parseChefApplication(applicationRaw);
      const parsedKitchen = kitchenRaw === null ? null : parseChefKitchen(kitchenRaw);
      const parsedMenu = parseChefMenuItems(menuRaw);
      if (!parsedApplication) {
        throw new Error("Craves returned an invalid chef application response.");
      }
      if (kitchenRaw !== null && !parsedKitchen) {
        throw new Error("Craves returned an invalid kitchen response.");
      }
      if (!parsedMenu) {
        throw new Error("Craves returned an invalid chef menu response.");
      }

      setApplication(parsedApplication);
      setKitchen(parsedKitchen);
      setMenu(parsedMenu);
      setState("ready");
      setLastUpdatedAt(new Date());
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : "Chef operations are temporarily unavailable.",
      );
      setState("error");
    } finally {
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const metrics = useMemo(() => {
    const activeItems = menu.filter((item) => item.status === "ACTIVE");
    const availableItems = activeItems.filter((item) => item.available);
    const withImages = activeItems.filter((item) => item.images.length > 0);
    return {
      activeItems: activeItems.length,
      availableItems: availableItems.length,
      withImages: withImages.length,
    };
  }, [menu]);

  const applicationApproved = application?.status === "APPROVED";
  const requiredDocumentTypes = new Set(
    application?.documents.map((document) => document.documentType) ?? [],
  );
  const hasSupportedProofs =
    requiredDocumentTypes.has("AADHAAR_CARD") &&
    requiredDocumentTypes.has("PAN_CARD");
  const kitchenActive = kitchen?.status === "ACTIVE";
  const locationMapped =
    typeof kitchen?.latitude === "number" &&
    typeof kitchen.longitude === "number";
  const discoverable =
    applicationApproved &&
    kitchenActive &&
    locationMapped &&
    metrics.availableItems > 0;

  if (state === "loading") {
    return (
      <div className="grid gap-4 md:grid-cols-2" aria-hidden="true">
        {Array.from({ length: 6 }, (_, index) => (
          <div key={index} className="h-44 animate-pulse rounded-2xl bg-grey-200" />
        ))}
        <p className="sr-only" role="status">
          Loading chef operations and compliance status
        </p>
      </div>
    );
  }

  if (state === "error") {
    return (
      <section className="rounded-2xl border border-error/20 bg-white p-8 text-center shadow-[var(--shadow-card)] md:p-12">
        <AlertTriangle className="mx-auto h-10 w-10 text-error" aria-hidden="true" />
        <h2 className="mt-4 font-display text-2xl font-bold text-ink">
          Operations status unavailable
        </h2>
        <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">
          {error}
        </p>
        <button type="button" onClick={() => void load()} className="btn-primary mt-6">
          <RefreshCw className="h-4 w-4" aria-hidden="true" /> Retry
        </button>
      </section>
    );
  }

  return (
    <div className="space-y-6">
      <section
        className={`rounded-2xl border p-5 shadow-[var(--shadow-card)] md:p-6 ${
          discoverable
            ? "border-success/20 bg-success/5"
            : "border-warning/20 bg-warning/5"
        }`}
      >
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            {discoverable ? (
              <CheckCircle2 className="mt-1 h-7 w-7 shrink-0 text-success" aria-hidden="true" />
            ) : (
              <ShieldAlert className="mt-1 h-7 w-7 shrink-0 text-warning" aria-hidden="true" />
            )}
            <div>
              <p className="craves-overline text-ink/65">Customer discovery readiness</p>
              <h2 className="mt-1 font-display text-2xl font-bold text-ink">
                {discoverable
                  ? "Kitchen is operationally discoverable"
                  : "Complete the required operational states"}
              </h2>
              <p className="mt-2 max-w-3xl text-sm leading-6 text-muted-foreground">
                Discovery requires an approved chef application, an ACTIVE kitchen with a confirmed mapped location and at least one ACTIVE and available menu item. The backend remains authoritative for every request.
              </p>
            </div>
          </div>
          <button
            type="button"
            disabled={refreshing}
            onClick={() => void load(true)}
            className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-border bg-white px-4 text-sm font-semibold text-ink hover:border-primary disabled:opacity-50"
          >
            <RefreshCw
              className={`h-4 w-4 ${refreshing ? "animate-spin" : ""}`}
              aria-hidden="true"
            />
            Refresh
          </button>
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <article className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]">
          <div className="flex items-center justify-between gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-secondary text-primary">
              <ShieldCheck className="h-5 w-5" aria-hidden="true" />
            </span>
            <span className={`rounded-full border px-3 py-1 text-xs font-bold ${statusTone(applicationApproved)}`}>
              {application?.status.replaceAll("_", " ")}
            </span>
          </div>
          <h3 className="mt-4 font-display text-lg font-bold text-ink">
            Chef approval
          </h3>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">
            Admin review controls access to chef-owned kitchen, menu, order and finance APIs.
          </p>
          {application?.reviewedAt && (
            <p className="mt-3 text-xs text-muted-foreground">
              Reviewed {new Date(application.reviewedAt).toLocaleString("en-IN")}
            </p>
          )}
          {application?.rejectionReason && (
            <p className="mt-3 rounded-xl bg-error/5 p-3 text-xs leading-5 text-error">
              Review note: {application.rejectionReason}
            </p>
          )}
          <Link href="/chef/application" className="mt-5 inline-flex min-h-11 items-center text-sm font-semibold text-contrast-red">
            Review application
          </Link>
        </article>

        <article className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]">
          <div className="flex items-center justify-between gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-secondary text-primary">
              <FileCheck2 className="h-5 w-5" aria-hidden="true" />
            </span>
            <span className={`rounded-full border px-3 py-1 text-xs font-bold ${statusTone(hasSupportedProofs)}`}>
              {application?.documents.length ?? 0} uploaded
            </span>
          </div>
          <h3 className="mt-4 font-display text-lg font-bold text-ink">
            Supported proof evidence
          </h3>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">
            Current backend evidence types are Aadhaar card and PAN card. Their document statuses are controlled by the chef-application service.
          </p>
          <ul className="mt-3 space-y-2 text-xs text-muted-foreground">
            {(application?.documents ?? []).map((document) => (
              <li key={document.id} className="flex items-center justify-between gap-3 rounded-lg bg-cream px-3 py-2">
                <span>{document.documentType.replaceAll("_", " ")}</span>
                <strong className="text-ink">{document.status}</strong>
              </li>
            ))}
          </ul>
          <p className="mt-3 text-xs leading-5 text-muted-foreground">
            Craves does not invent FSSAI eligibility or expiry rules in the UI. Add new compliance types only after the product/legal contract is approved.
          </p>
        </article>

        <article className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]">
          <div className="flex items-center justify-between gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-secondary text-primary">
              <Store className="h-5 w-5" aria-hidden="true" />
            </span>
            <span className={`rounded-full border px-3 py-1 text-xs font-bold ${statusTone(kitchenActive)}`}>
              {kitchen?.status ?? "NOT CREATED"}
            </span>
          </div>
          <h3 className="mt-4 font-display text-lg font-bold text-ink">
            Kitchen availability
          </h3>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">
            Kitchen status is the current backend-supported operational switch. INACTIVE pauses discovery without fabricating weekly opening hours.
          </p>
          <Link href="/chef/kitchen" className="mt-5 inline-flex min-h-11 items-center text-sm font-semibold text-contrast-red">
            Manage kitchen
          </Link>
        </article>

        <article className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]">
          <div className="flex items-center justify-between gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-secondary text-primary">
              <MapPinned className="h-5 w-5" aria-hidden="true" />
            </span>
            <span className={`rounded-full border px-3 py-1 text-xs font-bold ${statusTone(locationMapped)}`}>
              {locationMapped ? "MAPPED" : "MISSING"}
            </span>
          </div>
          <h3 className="mt-4 font-display text-lg font-bold text-ink">
            Kitchen location
          </h3>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">
            Craves keeps the precise kitchen map point securely in the background so nearby discovery and delivery pickup can work without exposing technical location values to chefs.
          </p>
          <Link href="/chef/kitchen" className="mt-5 inline-flex min-h-11 items-center text-sm font-semibold text-contrast-red">
            {locationMapped ? "Review kitchen address" : "Confirm kitchen location"}
          </Link>
        </article>

        <article className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]">
          <div className="flex items-center justify-between gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-secondary text-primary">
              <Utensils className="h-5 w-5" aria-hidden="true" />
            </span>
            <span className={`rounded-full border px-3 py-1 text-xs font-bold ${statusTone(metrics.availableItems > 0)}`}>
              {metrics.availableItems} available
            </span>
          </div>
          <h3 className="mt-4 font-display text-lg font-bold text-ink">
            Menu availability
          </h3>
          <dl className="mt-3 grid grid-cols-2 gap-3 text-sm">
            <div className="rounded-xl bg-cream p-3">
              <dt className="text-xs text-muted-foreground">Active</dt>
              <dd className="mt-1 font-display text-xl font-bold text-ink">{metrics.activeItems}</dd>
            </div>
            <div className="rounded-xl bg-cream p-3">
              <dt className="text-xs text-muted-foreground">Available</dt>
              <dd className="mt-1 font-display text-xl font-bold text-ink">{metrics.availableItems}</dd>
            </div>
          </dl>
          <Link href="/chef/menu" className="mt-5 inline-flex min-h-11 items-center text-sm font-semibold text-contrast-red">
            Manage menu
          </Link>
        </article>

        <article className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]">
          <div className="flex items-center justify-between gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-secondary text-primary">
              <ImageIcon className="h-5 w-5" aria-hidden="true" />
            </span>
            <span className={`rounded-full border px-3 py-1 text-xs font-bold ${statusTone(metrics.withImages === metrics.activeItems && metrics.activeItems > 0)}`}>
              {metrics.withImages}/{metrics.activeItems}
            </span>
          </div>
          <h3 className="mt-4 font-display text-lg font-bold text-ink">
            Active dish images
          </h3>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">
            Active dishes without approved public images remain valid but show the Craves placeholder to customers.
          </p>
          <Link href="/chef/menu/media" className="mt-5 inline-flex min-h-11 items-center text-sm font-semibold text-contrast-red">
            Images and availability
          </Link>
        </article>
      </section>

      <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
        <div className="flex items-start gap-3">
          <Clock3 className="mt-1 h-6 w-6 shrink-0 text-primary" aria-hidden="true" />
          <div>
            <h2 className="font-display text-xl font-bold text-ink">
              Schedule support without invented rules
            </h2>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">
              The current backend exposes kitchen status and per-item availability, but no reviewed weekly opening-hours contract. This workspace therefore uses only those real controls. A weekly schedule must be designed and approved in the functional and architecture documents before implementation.
            </p>
          </div>
        </div>
      </section>

      {lastUpdatedAt && (
        <p className="text-xs text-muted-foreground">
          Last refreshed {lastUpdatedAt.toLocaleString("en-IN")}
        </p>
      )}
    </div>
  );
}

export default ChefOperationsWorkspace;
