import { useState } from "react";
import { ChevronDown, ChevronUp } from "lucide-react";

/** "About this dish" heading + description with an expand/collapse toggle. */
export function AboutDishSection({ description }: { description: string }) {
  const [expanded, setExpanded] = useState(false);
  const isLong = description.length > 110;

  return (
    <section className="mt-6">
      <h2 className="font-display text-lg font-bold text-ink">About this dish</h2>
      <p className={`mt-1.5 text-sm text-ink/80 ${!expanded && isLong ? "line-clamp-2" : ""}`}>
        {description}
      </p>
      {isLong && (
        <button
          type="button"
          onClick={() => setExpanded((v) => !v)}
          className="mt-1 flex items-center gap-1 text-sm font-semibold text-primary"
        >
          {expanded ? "Read Less" : "Read More"}
          {expanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
        </button>
      )}
    </section>
  );
}

export default AboutDishSection;
