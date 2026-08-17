import { Layers3, MapPin, Package } from "lucide-react";
import type { Chef } from "@/services/api/chefs";

export function ChefStatsRow({ chef }: { chef: Chef }) {
  const stats = [
    {
      icon: Package,
      value: chef.activeDishCount,
      label: "Available dishes",
    },
    {
      icon: Layers3,
      value: chef.specialties.length,
      label: "Menu categories",
    },
    {
      icon: MapPin,
      value: chef.distanceKm > 0 ? `${chef.distanceKm} km` : "—",
      label: "From your address",
    },
  ];

  return (
    <dl className="mt-4 grid grid-cols-3 gap-2.5">
      {stats.map((stat) => (
        <div
          key={stat.label}
          className="rounded-xl border border-border bg-white px-3 py-3 text-center shadow-[var(--shadow-card)]"
        >
          <dt className="text-[0.68rem] leading-4 text-muted-foreground">
            <stat.icon className="mx-auto mb-1 h-5 w-5 text-primary" aria-hidden="true" />
            {stat.label}
          </dt>
          <dd className="order-first font-display text-lg font-bold text-ink">
            {stat.value}
          </dd>
        </div>
      ))}
    </dl>
  );
}

export default ChefStatsRow;
