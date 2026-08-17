import { Star, Heart } from "lucide-react";
import { customerReviews } from "@/constants/landingContent";

/** "Loved by Thousands" — customer review cards. */
export function TestimonialsSection() {
  return (
    <section className="mx-auto max-w-7xl px-6 pb-20">
      <div className="text-center">
        <h2 className="text-4xl font-bold text-ink md:text-5xl">Loved by Thousands</h2>
        <div className="my-4 flex items-center justify-center gap-2">
          <span className="h-px w-8 bg-primary" />
          <Heart className="h-4 w-4 fill-primary text-primary" />
          <span className="h-px w-8 bg-primary" />
        </div>
      </div>
      <div className="mt-10 grid gap-6 md:grid-cols-3">
        {customerReviews.map((r) => (
          <div key={r.name} className="rounded-2xl border border-border bg-card p-6 shadow-sm">
            <div className="flex gap-1">
              {Array.from({ length: 5 }).map((_, i) => (
                <Star key={i} className="h-4 w-4 fill-primary text-primary" />
              ))}
            </div>
            <p className="mt-4 text-sm italic text-foreground">&ldquo;{r.text}&rdquo;</p>
            <p className="mt-4 text-sm font-semibold text-primary">– {r.name}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

export default TestimonialsSection;
