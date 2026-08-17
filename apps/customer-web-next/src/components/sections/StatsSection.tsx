import { platformStats } from "@/constants/landingContent";

/** Customers / chefs / orders / cities stat strip. */
export function StatsSection() {
  return (
    <section className="bg-cream-deep py-14">
      <div className="mx-auto grid max-w-7xl grid-cols-2 gap-8 px-6 md:grid-cols-4">
        {platformStats.map((s) => (
          <div key={s.label} className="flex items-center justify-center gap-4 text-center">
            <s.icon className="h-10 w-10 text-primary" strokeWidth={1.5} />
            <div className="text-left">
              <div className="text-2xl font-bold text-ink md:text-3xl">{s.value}</div>
              <div className="text-xs text-muted-foreground md:text-sm">{s.label}</div>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

export default StatsSection;
