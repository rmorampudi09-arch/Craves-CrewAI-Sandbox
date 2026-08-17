/** Ingredient pill list ("Basmati Rice, Chicken, Fried Onions, Whole Spices, +5"). */
export function WhatsInsideCard({ ingredients }: { ingredients: string[] }) {
  if (!ingredients || ingredients.length === 0) return null;
  const visible = ingredients.slice(0, 4);
  const remaining = ingredients.length - visible.length;

  return (
    <section className="mt-6">
      <h2 className="font-display text-lg font-bold text-ink">Ingredients</h2>
      <div className="mt-2 flex flex-wrap gap-2">
        {visible.map((i) => (
          <span
            key={i}
            className="rounded-full border border-border bg-card px-3 py-1.5 text-xs font-medium text-ink"
          >
            {i}
          </span>
        ))}
        {remaining > 0 && (
          <span className="rounded-full border border-border bg-white px-3 py-1.5 text-xs font-medium text-muted-foreground">
            +{remaining}
          </span>
        )}
      </div>
    </section>
  );
}

export default WhatsInsideCard;
