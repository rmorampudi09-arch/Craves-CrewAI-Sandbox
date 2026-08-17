import type { Chef } from "@/services/api/chefs";

export function ChefAboutSection({ chef }: { chef: Chef }) {
  if (!chef.bio && chef.specialties.length === 0) return null;

  return (
    <section className="mt-6 rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]">
      <h2 className="font-display text-lg font-bold text-ink">
        {chef.bio ? "About this kitchen" : "Menu categories"}
      </h2>
      {chef.bio && <p className="mt-2 text-sm leading-6 text-muted-foreground">{chef.bio}</p>}
      {chef.specialties.length > 0 && (
        <div className="mt-4 flex flex-wrap gap-2" aria-label="Available menu categories">
          {chef.specialties.map((specialty) => (
            <span
              key={specialty}
              className="rounded-full border border-border bg-cream px-3 py-1.5 text-xs font-semibold text-ink"
            >
              {specialty}
            </span>
          ))}
        </div>
      )}
    </section>
  );
}

export default ChefAboutSection;
