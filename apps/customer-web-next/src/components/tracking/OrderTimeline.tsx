import { Check } from "lucide-react";

export interface TrackingStep {
  key: string;
  label: string;
  desc: string;
}

interface OrderTimelineProps {
  steps: TrackingStep[];
  currentIndex: number;
}

/** Vertical connected-dot timeline showing each status step, done/active/upcoming. */
export function OrderTimeline({ steps, currentIndex }: OrderTimelineProps) {
  return (
    <section className="mt-5 rounded-2xl border border-border bg-card p-5">
      <ol className="relative space-y-6 pl-8">
        <span className="absolute left-3 top-2 h-[calc(100%-16px)] w-0.5 bg-border" aria-hidden />
        {steps.map((s, i) => {
          const done = i <= currentIndex;
          const active = i === currentIndex;
          return (
            <li key={s.key} className="relative">
              <span
                className={`absolute -left-8 flex h-6 w-6 items-center justify-center rounded-full border-2 ${
                  done
                    ? "border-primary bg-primary text-primary-foreground"
                    : "border-border bg-white text-muted-foreground"
                }`}
              >
                {done ? (
                  <Check className="h-3.5 w-3.5" />
                ) : (
                  <span className="h-1.5 w-1.5 rounded-full bg-muted-foreground" />
                )}
              </span>
              <p
                className={`font-semibold ${active ? "text-primary" : done ? "text-ink" : "text-muted-foreground"}`}
              >
                {s.label}
              </p>
              <p className="text-xs text-muted-foreground">{s.desc}</p>
            </li>
          );
        })}
      </ol>
    </section>
  );
}

export default OrderTimeline;
