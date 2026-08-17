import {
  AlertTriangle,
  ArrowRight,
  ChefHat,
  MapPin,
  RefreshCw,
  SearchX,
  Utensils,
} from "lucide-react";
import {
  formatDistance,
  type NearbyKitchen,
} from "@/lib/discovery-contract";

type DiscoveryState = "loading" | "ready" | "error" | "address-required";

interface KitchensGridProps {
  kitchens: NearbyKitchen[];
  searchTerm: string;
  state: DiscoveryState;
  message: string;
  onSelectKitchen: (kitchen: NearbyKitchen) => void;
  onRetry: () => void;
  onManageAddress: () => void;
}

function KitchenSkeleton() {
  return (
    <div
      className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]"
      aria-hidden="true"
    >
      <div className="h-12 w-12 animate-pulse rounded-full bg-grey-200" />
      <div className="mt-5 h-6 w-2/3 animate-pulse rounded bg-grey-200" />
      <div className="mt-3 h-4 w-1/2 animate-pulse rounded bg-grey-200" />
      <div className="mt-5 h-11 w-full animate-pulse rounded-lg bg-grey-200" />
    </div>
  );
}

export function KitchensGrid({
  kitchens,
  searchTerm,
  state,
  message,
  onSelectKitchen,
  onRetry,
  onManageAddress,
}: KitchensGridProps) {
  const normalizedSearch = searchTerm.trim();

  return (
    <section
      className="mx-auto max-w-7xl px-4 pb-24 pt-6 md:px-6"
      aria-labelledby="nearby-kitchens-heading"
    >
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="craves-overline text-primary">Nearby home kitchens</p>
          <h2
            id="nearby-kitchens-heading"
            className="mt-1 font-display text-2xl font-bold tracking-[-0.035em] text-ink md:text-3xl"
          >
            Choose a kitchen to view its menu
          </h2>
        </div>
        {state === "ready" && (
          <span className="rounded-full bg-secondary px-3 py-1.5 text-xs font-semibold text-muted-foreground">
            {kitchens.length} {kitchens.length === 1 ? "kitchen" : "kitchens"}
          </span>
        )}
      </div>

      {state === "loading" && (
        <div>
          <p className="sr-only" role="status">
            Loading nearby kitchens
          </p>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {Array.from({ length: 8 }, (_, index) => (
              <KitchenSkeleton key={index} />
            ))}
          </div>
        </div>
      )}

      {state === "address-required" && (
        <div className="rounded-2xl border border-border bg-white p-8 text-center shadow-[var(--shadow-card)] md:p-12">
          <MapPin className="mx-auto h-10 w-10 text-primary" aria-hidden="true" />
          <h3 className="mt-4 font-display text-xl font-bold text-ink">
            A mapped delivery address is required
          </h3>
          <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">
            {message}
          </p>
          <button type="button" onClick={onManageAddress} className="btn-primary mt-6">
            Manage delivery addresses
          </button>
        </div>
      )}

      {state === "error" && (
        <div className="rounded-2xl border border-error/20 bg-white p-8 text-center shadow-[var(--shadow-card)] md:p-12">
          <AlertTriangle className="mx-auto h-10 w-10 text-error" aria-hidden="true" />
          <h3 className="mt-4 font-display text-xl font-bold text-ink">
            Nearby kitchens could not be loaded
          </h3>
          <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">
            {message}
          </p>
          <button type="button" onClick={onRetry} className="btn-primary mt-6">
            <RefreshCw className="h-4 w-4" aria-hidden="true" /> Try again
          </button>
        </div>
      )}

      {state === "ready" && kitchens.length === 0 && (
        <div className="rounded-2xl border border-dashed border-border bg-white p-8 text-center md:p-12">
          <SearchX className="mx-auto h-10 w-10 text-muted-foreground" aria-hidden="true" />
          <h3 className="mt-4 font-display text-xl font-bold text-ink">
            {normalizedSearch ? "No kitchens match your search" : "No nearby kitchens found"}
          </h3>
          <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">
            {normalizedSearch
              ? `No nearby kitchen matches “${normalizedSearch}”. Try another search.`
              : message || "No active home kitchens are available for this delivery location yet."}
          </p>
          {!normalizedSearch && (
            <button
              type="button"
              onClick={onRetry}
              className="mt-5 min-h-11 rounded-lg border border-primary px-4 text-sm font-semibold text-contrast-red hover:bg-secondary"
            >
              Refresh nearby kitchens
            </button>
          )}
        </div>
      )}

      {state === "ready" && kitchens.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {kitchens.map((kitchen) => {
            const name = kitchen.displayName || kitchen.kitchenName;
            const location = [kitchen.areaName, kitchen.city]
              .filter(Boolean)
              .join(", ");

            return (
              <article
                key={kitchen.id}
                className="flex h-full flex-col rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] transition duration-[var(--motion-fast)] hover:-translate-y-1 hover:border-primary/35"
              >
                <div className="flex items-start justify-between gap-3">
                  <span className="inline-flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-secondary text-primary">
                    <ChefHat className="h-6 w-6" aria-hidden="true" />
                  </span>
                  <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-semibold text-muted-foreground">
                    {formatDistance(kitchen.distanceMeters)}
                  </span>
                </div>

                <h3 className="mt-5 font-display text-xl font-bold tracking-[-0.025em] text-ink">
                  {name}
                </h3>
                <p className="mt-2 flex items-center gap-1.5 text-sm font-medium text-muted-foreground">
                  <MapPin className="h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                  {location || `${kitchen.city}, ${kitchen.state}`}
                </p>

                {kitchen.description && (
                  <p className="mt-3 line-clamp-2 text-sm leading-5 text-muted-foreground">
                    {kitchen.description}
                  </p>
                )}

                <div className="mt-4 flex items-center gap-2 text-sm font-semibold text-ink">
                  <Utensils className="h-4 w-4 text-primary" aria-hidden="true" />
                  {kitchen.activeMenuItemCount} active {kitchen.activeMenuItemCount === 1 ? "dish" : "dishes"}
                </div>

                <button
                  type="button"
                  onClick={() => onSelectKitchen(kitchen)}
                  className="mt-5 inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-primary px-4 text-sm font-semibold text-white transition-colors hover:bg-contrast-red"
                >
                  View menu
                  <ArrowRight className="h-4 w-4" aria-hidden="true" />
                </button>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}

export default KitchensGrid;
