import { Link } from "@tanstack/react-router";
import { useState } from "react";
import { Check, Clock, MapPin, Plus, ShoppingBag } from "lucide-react";
import { addToCart } from "@/services/api/cravesCart";
import type { Dish } from "@/services/api/dishes";

function distanceLabel(distanceMeters?: number): string | null {
  if (typeof distanceMeters !== "number") return null;
  return distanceMeters < 1_000
    ? `${Math.round(distanceMeters)} m`
    : `${(distanceMeters / 1_000).toFixed(1)} km`;
}

function priceLabel(price: number, currency = "INR"): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(price);
}

export function DishCard({ dish }: { dish: Dish }) {
  const [state, setState] = useState<"idle" | "adding" | "added" | "error">("idle");
  const [message, setMessage] = useState<string | null>(null);
  const distance = distanceLabel(dish.distanceMeters);

  const handleAdd = async () => {
    if (state === "adding") return;
    setState("adding");
    setMessage(null);
    try {
      await addToCart(
        {
          id: dish.id,
          name: dish.name,
          chef: dish.chef,
          price: dish.price,
          img: dish.img,
        },
        1,
      );
      setState("added");
      setMessage(`${dish.name} was added to the cart.`);
      window.setTimeout(() => setState("idle"), 1600);
    } catch (error) {
      setState("error");
      setMessage(error instanceof Error ? error.message : "This dish could not be added to the cart.");
    }
  };

  return (
    <article className="group flex h-full flex-col overflow-hidden rounded-2xl border border-border bg-white shadow-[var(--shadow-card)] transition duration-[var(--motion-fast)] hover:-translate-y-1 hover:border-primary/35">
      <div className="relative aspect-[4/3] overflow-hidden bg-grey-200">
        <Link
          to="/dish/$id"
          params={{ id: dish.id }}
          className="absolute inset-0 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-primary/35 focus-visible:ring-inset"
          aria-label={`View ${dish.name} details`}
        >
          <img
            src={dish.img}
            alt={dish.name}
            width={1024}
            height={768}
            loading="lazy"
            className="h-full w-full object-cover transition duration-[var(--motion-slow)] group-hover:scale-[1.03]"
          />
        </Link>
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-20 bg-gradient-to-t from-ink/60 to-transparent" />
        <span className="pointer-events-none absolute left-3 top-3 rounded-full bg-white/95 px-2.5 py-1 text-[0.68rem] font-bold uppercase tracking-[0.07em] text-ink shadow-sm">
          {dish.category}
        </span>
        <span
          className={`pointer-events-none absolute bottom-3 left-3 inline-flex items-center gap-1.5 rounded-full bg-white/95 px-2.5 py-1 text-[0.68rem] font-bold ${dish.veg ? "text-success" : "text-error"}`}
        >
          <span className={`h-2 w-2 rounded-full ${dish.veg ? "bg-success" : "bg-error"}`} />
          {dish.veg ? "Veg" : "Non-veg"}
        </span>
        {distance && (
          <span className="pointer-events-none absolute bottom-3 right-3 inline-flex items-center gap-1 rounded-full bg-ink/85 px-2.5 py-1 text-[0.68rem] font-semibold text-white">
            <MapPin className="h-3 w-3" aria-hidden="true" /> {distance}
          </span>
        )}
      </div>

      <div className="flex flex-1 flex-col p-4">
        <div>
          <Link to="/dish/$id" params={{ id: dish.id }} className="rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/35">
            <h3 className="font-display text-lg font-bold leading-6 tracking-[-0.025em] text-ink group-hover:text-contrast-red">
              {dish.name}
            </h3>
          </Link>
          <p className="mt-1 text-sm font-medium text-muted-foreground">{dish.chef}</p>
        </div>

        {dish.desc && <p className="mt-3 line-clamp-2 text-sm leading-5 text-muted-foreground">{dish.desc}</p>}

        <div className="mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs font-medium text-muted-foreground">
          <span className="inline-flex items-center gap-1.5">
            <Clock className="h-3.5 w-3.5" aria-hidden="true" /> {dish.time}
          </span>
          {dish.serves && <span>{dish.serves}</span>}
        </div>

        <div className="mt-auto flex items-end justify-between gap-3 pt-5">
          <div>
            <span className="block text-[0.68rem] font-semibold uppercase tracking-[0.08em] text-muted-foreground">Price</span>
            <span className="font-display text-xl font-bold text-ink">{priceLabel(dish.price, dish.currency)}</span>
          </div>
          <button
            type="button"
            onClick={() => void handleAdd()}
            disabled={state === "adding"}
            className="inline-flex min-h-11 items-center justify-center gap-2 rounded-lg bg-primary px-4 text-sm font-semibold text-white transition-colors hover:bg-contrast-red disabled:cursor-wait disabled:opacity-60"
            aria-label={`Add ${dish.name} to cart`}
          >
            {state === "adding" ? (
              <ShoppingBag className="h-4 w-4 animate-pulse" aria-hidden="true" />
            ) : state === "added" ? (
              <Check className="h-4 w-4" aria-hidden="true" />
            ) : (
              <Plus className="h-4 w-4" aria-hidden="true" />
            )}
            {state === "adding" ? "Adding" : state === "added" ? "Added" : "Add"}
          </button>
        </div>

        {message && (
          <p className={`mt-3 text-xs font-medium ${state === "error" ? "text-error" : "text-success"}`} role={state === "error" ? "alert" : "status"}>
            {message}
          </p>
        )}
      </div>
    </article>
  );
}

export default DishCard;
