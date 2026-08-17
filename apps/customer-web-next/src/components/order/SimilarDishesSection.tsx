import { Link } from "@tanstack/react-router";
import { Star } from "lucide-react";
import { WishlistHeartButton } from "@/components/order/WishlistHeartButton";
import type { Dish } from "@/services/api/dishes";

/** "Similar Dishes" heading + "See All" link, then a horizontally-scrolling card rail. */
export function SimilarDishesSection({ dishes }: { dishes: Dish[] }) {
  if (!dishes || dishes.length === 0) return null;

  return (
    <section className="mt-6">
      <div className="flex items-center justify-between">
        <h2 className="font-display text-lg font-bold text-ink">Similar Dishes</h2>
        <button type="button" className="text-sm font-semibold text-primary">
          See All
        </button>
      </div>
      <div className="mt-2 flex gap-3 overflow-x-auto pb-1">
        {dishes.map((d) => (
          <Link
            key={d.id}
            to="/dish/$id"
            params={{ id: d.id }}
            className="w-36 shrink-0 overflow-hidden rounded-2xl border border-border bg-card"
          >
            <div className="relative aspect-square">
              <img src={d.img} alt={d.name} className="h-full w-full object-cover" />
              <WishlistHeartButton
                item={{ id: d.id, name: d.name, chef: d.chef, price: d.price, img: d.img }}
                className="absolute right-2 top-2"
              />
            </div>
            <div className="p-2.5">
              <p className="truncate text-sm font-semibold text-ink">{d.name}</p>
              <div className="mt-1 flex items-center justify-between text-xs">
                <span className="font-bold text-ink">₹{d.price}</span>
                <span className="flex items-center gap-0.5 text-muted-foreground">
                  <Star className="h-3 w-3 fill-primary text-primary" /> {d.rating}
                </span>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </section>
  );
}

export default SimilarDishesSection;
