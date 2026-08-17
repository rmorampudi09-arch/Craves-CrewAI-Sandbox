import { Star } from "lucide-react";
import type { Dish } from "@/services/api/dishes";

function FoodMark({ veg }: { veg: boolean }) {
  return (
    <span
      className={`inline-flex h-5 w-5 shrink-0 items-center justify-center rounded border-2 bg-white align-middle ${
        veg ? "border-success" : "border-error"
      }`}
      aria-label={veg ? "Vegetarian" : "Non-vegetarian"}
    >
      <span className={`h-2.5 w-2.5 rounded-full ${veg ? "bg-success" : "bg-error"}`} />
    </span>
  );
}

export function DishInfoSummary({ dish }: { dish: Dish }) {
  return (
    <div>
      <p className="craves-overline text-primary">{dish.category}</p>
      <h1 className="mt-2 flex items-start gap-2 font-display text-3xl font-bold leading-tight tracking-[-0.04em] text-ink md:text-4xl">
        <span>{dish.name}</span>
        <FoodMark veg={dish.veg} />
      </h1>
      <p className="mt-3 text-base leading-6 text-muted-foreground">{dish.desc}</p>
      {dish.rating > 0 && (
        <p className="mt-3 flex items-center gap-1.5 text-sm text-ink">
          <Star className="h-4 w-4 fill-[#F5B400] text-[#F5B400]" aria-hidden="true" />
          <span className="font-semibold">{dish.rating.toFixed(1)}</span>
          {dish.reviewCount ? (
            <span className="text-muted-foreground">· {dish.reviewCount} verified reviews</span>
          ) : null}
        </p>
      )}
    </div>
  );
}

export default DishInfoSummary;
