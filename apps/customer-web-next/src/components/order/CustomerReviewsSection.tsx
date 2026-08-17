import { Star, User } from "lucide-react";

interface Review {
  name: string;
  rating: number;
  daysAgo: number;
  text: string;
}

/** "Customer Reviews" heading + "See All" link, followed by a stack of review cards. */
export function CustomerReviewsSection({ reviews }: { reviews: Review[] }) {
  if (!reviews || reviews.length === 0) return null;

  return (
    <section className="mt-6">
      <div className="flex items-center justify-between">
        <h2 className="font-display text-lg font-bold text-ink">Customer Reviews</h2>
        <button type="button" className="text-sm font-semibold text-primary">
          See All
        </button>
      </div>
      <div className="mt-2 space-y-3">
        {reviews.map((r) => (
          <div key={r.name} className="rounded-2xl border border-border bg-card p-4">
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary/10 text-primary">
                <User className="h-4 w-4" />
              </div>
              <div className="min-w-0">
                <p className="truncate font-semibold text-ink">{r.name}</p>
                <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <span className="flex items-center gap-0.5">
                    {Array.from({ length: r.rating }).map((_, i) => (
                      <Star key={i} className="h-3 w-3 fill-primary text-primary" />
                    ))}
                  </span>
                  {r.daysAgo === 0 ? "Today" : `${r.daysAgo} day${r.daysAgo > 1 ? "s" : ""} ago`}
                </p>
              </div>
            </div>
            <p className="mt-2 text-sm text-ink/80">{r.text}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

export default CustomerReviewsSection;
