/** "Currently: {label}" callout showing the order's current status step. */
export function CurrentStatusCard({ label, desc }: { label: string; desc: string }) {
  return (
    <section className="rounded-2xl border border-border bg-card p-5">
      <p className="font-script text-primary">Currently</p>
      <h2 className="font-display text-2xl font-bold text-ink">{label}</h2>
      <p className="mt-1 text-sm text-muted-foreground">{desc}</p>
    </section>
  );
}

export default CurrentStatusCard;
