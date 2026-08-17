import { ChefHat, MapPin, Star } from "lucide-react";
import type { Chef } from "@/services/api/chefs";

export function ChefProfileHero({ chef }: { chef: Chef }) {
  return (
    <section className="overflow-hidden rounded-2xl bg-ink p-6 text-white shadow-[var(--shadow-card)]">
      <div className="flex items-center gap-4">
        <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-2xl bg-primary text-white">
          <ChefHat className="h-10 w-10" aria-hidden="true" />
        </div>
        <div className="min-w-0">
          <p className="craves-overline text-[#F5B400]">Home kitchen</p>
          <h1 className="mt-1 truncate font-display text-2xl font-bold tracking-[-0.035em]">
            {chef.name}
          </h1>
          {chef.rating > 0 && (
            <p className="mt-2 flex items-center gap-1.5 text-sm text-white/90">
              <Star className="h-4 w-4 fill-[#F5B400] text-[#F5B400]" aria-hidden="true" />
              {chef.rating.toFixed(1)}
              {chef.reviewCount > 0 && (
                <span className="text-white/65">({chef.reviewCount} verified reviews)</span>
              )}
            </p>
          )}
          {chef.location && (
            <p className="mt-2 flex items-start gap-1.5 text-xs leading-5 text-white/70">
              <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              <span>
                {chef.location}
                {chef.distanceKm > 0 ? ` · ${chef.distanceKm} km away` : ""}
              </span>
            </p>
          )}
        </div>
      </div>
    </section>
  );
}

export default ChefProfileHero;
