import { Clock, Flame, Users, Leaf, Drumstick } from "lucide-react";
import type { Dish } from "@/services/api/dishes";

/** Row of 4 small info chips, matching the reference design's quick-facts strip. */
export function QuickInfoChips({ dish }: { dish: Dish }) {
  const chips = [
    { icon: Clock, label: "Prep Time", value: dish.time },
    { icon: Flame, label: "Spice Level", value: dish.spiceLevel ?? "Medium" },
    { icon: Users, label: "Serves", value: dish.serves ?? "1 Person" },
    {
      icon: dish.veg ? Leaf : Drumstick,
      label: "Type",
      value: dish.veg ? "Veg" : "Non-Veg",
    },
  ];

  return (
    <div className="mt-5 grid grid-cols-2 gap-2.5 sm:grid-cols-4">
      {chips.map((c) => (
        <div key={c.label} className="rounded-xl border border-border bg-card px-3 py-2.5">
          <c.icon className="h-4 w-4 text-primary" />
          <p className="mt-1 text-[11px] text-muted-foreground">{c.label}</p>
          <p className="text-sm font-semibold text-ink">{c.value}</p>
        </div>
      ))}
    </div>
  );
}

export default QuickInfoChips;
