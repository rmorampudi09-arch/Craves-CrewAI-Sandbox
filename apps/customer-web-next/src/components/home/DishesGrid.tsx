import { AlertTriangle, MapPin, RefreshCw, SearchX } from "lucide-react";
import { DishCard } from "@/components/home/DishCard";
import type { Dish } from "@/services/api/dishes";
import type { DishCategory } from "@/constants/dishCategories";

type DiscoveryState = "loading" | "ready" | "error" | "address-required";

interface DishesGridProps {
  dishes: Dish[];
  selectedCategory: DishCategory;
  searchTerm: string;
  state: DiscoveryState;
  message: string;
  onRetry: () => void;
  onManageAddress: () => void;
}

function DishSkeleton() {
  return (
    <div className="overflow-hidden rounded-2xl border border-border bg-white" aria-hidden="true">
      <div className="aspect-[4/3] animate-pulse bg-grey-200" />
      <div className="space-y-3 p-4">
        <div className="h-5 w-3/4 animate-pulse rounded bg-grey-200" />
        <div className="h-4 w-1/2 animate-pulse rounded bg-grey-200" />
        <div className="h-4 w-full animate-pulse rounded bg-grey-200" />
        <div className="h-11 w-full animate-pulse rounded-lg bg-grey-200" />
      </div>
    </div>
  );
}

export function DishesGrid({
  dishes,
  selectedCategory,
  searchTerm,
  state,
  message,
  onRetry,
  onManageAddress,
}: DishesGridProps) {
  const normalizedSearch = searchTerm.trim();
  const emptyMessage = normalizedSearch
    ? `No live dishes match “${normalizedSearch}”. Try another search.`
    : selectedCategory === "All"
      ? message || "No active dishes are available for this delivery location yet."
      : `No active ${selectedCategory.toLowerCase()} dishes are available for this delivery location yet.`;

  return (
    <section className="mx-auto max-w-7xl px-4 pb-24 pt-6 md:px-6" aria-labelledby="available-dishes-heading">
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="craves-overline text-primary">Live catalog</p>
          <h2 id="available-dishes-heading" className="mt-1 font-display text-2xl font-bold tracking-[-0.035em] text-ink md:text-3xl">
            {selectedCategory === "All" ? "Homemade dishes near you" : selectedCategory}
          </h2>
        </div>
        {state === "ready" && (
          <span className="rounded-full bg-secondary px-3 py-1.5 text-xs font-semibold text-muted-foreground" aria-live="polite">
            {dishes.length} {dishes.length === 1 ? "dish" : "dishes"}
          </span>
        )}
      </div>

      {state === "loading" && (
        <div>
          <p className="sr-only" role="status">Loading nearby dishes</p>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {Array.from({ length: 8 }, (_, index) => <DishSkeleton key={index} />)}
          </div>
        </div>
      )}

      {state === "address-required" && (
        <div className="rounded-2xl border border-border bg-white p-8 text-center shadow-[var(--shadow-card)] md:p-12">
          <MapPin className="mx-auto h-10 w-10 text-primary" aria-hidden="true" />
          <h3 className="mt-4 font-display text-xl font-bold text-ink">A mapped delivery address is required</h3>
          <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">{message}</p>
          <button type="button" onClick={onManageAddress} className="btn-primary mt-6">
            Manage delivery addresses
          </button>
        </div>
      )}

      {state === "error" && (
        <div className="rounded-2xl border border-error/20 bg-white p-8 text-center shadow-[var(--shadow-card)] md:p-12">
          <AlertTriangle className="mx-auto h-10 w-10 text-error" aria-hidden="true" />
          <h3 className="mt-4 font-display text-xl font-bold text-ink">Nearby dishes could not be loaded</h3>
          <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">{message}</p>
          <button type="button" onClick={onRetry} className="btn-primary mt-6">
            <RefreshCw className="h-4 w-4" aria-hidden="true" /> Try again
          </button>
        </div>
      )}

      {state === "ready" && dishes.length === 0 && (
        <div className="rounded-2xl border border-dashed border-border bg-white p-8 text-center md:p-12">
          <SearchX className="mx-auto h-10 w-10 text-muted-foreground" aria-hidden="true" />
          <h3 className="mt-4 font-display text-xl font-bold text-ink">Nothing available for this view</h3>
          <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">{emptyMessage}</p>
          {!normalizedSearch && (
            <button type="button" onClick={onRetry} className="mt-5 min-h-11 rounded-lg border border-primary px-4 text-sm font-semibold text-contrast-red hover:bg-secondary">
              Refresh live catalog
            </button>
          )}
        </div>
      )}

      {state === "ready" && dishes.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {dishes.map((dish) => <DishCard key={dish.id} dish={dish} />)}
        </div>
      )}
    </section>
  );
}

export default DishesGrid;
