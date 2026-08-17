import { Check, House } from "lucide-react";

import styles from "@/screens/public/LandingPage/LandingV2.module.css";

const benefits = [
  "Reach customers beyond your neighbourhood",
  "Flexible menu and availability",
  "Guidance for hygienic packing",
  "A platform built around your story",
] as const;

export function BecomeChefCtaSection({
  onBecomeChef,
}: {
  onBecomeChef: () => void;
}) {
  return (
    <section className="relative overflow-hidden bg-white py-24 md:py-28">
      <div className="mx-auto grid max-w-7xl gap-12 px-4 md:px-6 lg:grid-cols-[minmax(0,42rem)_26rem] lg:items-center lg:gap-20">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#F62E18]">
            For home chefs
          </p>
          <h2 className="mt-4 font-display text-4xl font-bold leading-[1.08] tracking-[-0.05em] text-[#111111] md:text-5xl lg:text-6xl">
            Your kitchen can create
            <span className="block">more than meals.</span>
          </h2>
          <p className="mt-7 max-w-2xl text-base leading-8 text-[#6E7378] md:text-lg">
            Turn the cooking you already love into meaningful income. Craves helps
            with discovery, ordering, customer reach, packaging standards and
            delivery — so you can focus on the food.
          </p>
          <button
            type="button"
            onClick={onBecomeChef}
            className={`${styles.primaryCta} mt-8 min-h-12 px-6 text-sm font-semibold`}
          >
            Start cooking with Craves
          </button>
        </div>

        <div className="relative">
          <div className="rounded-[1.875rem] bg-[#F6F7F8] p-8 md:p-9">
            <h3 className="text-lg font-bold text-[#111111]">Made for home chefs</h3>
            <ul className="mt-7 space-y-5">
              {benefits.map((benefit) => (
                <li key={benefit} className="flex items-start gap-3 text-sm leading-6 text-[#111111]">
                  <span className="mt-1 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-[#F62E18] text-white">
                    <Check className="h-3 w-3" strokeWidth={2.5} aria-hidden="true" />
                  </span>
                  <span>{benefit}</span>
                </li>
              ))}
            </ul>
          </div>

          <div
            aria-hidden="true"
            className={`${styles.sticker} absolute -bottom-8 right-2 hidden -rotate-3 items-center gap-2 rounded-2xl px-4 py-3 text-xs font-semibold text-[#111111] sm:flex`}
          >
            <House className="h-5 w-5 text-[#F62E18]" strokeWidth={1.8} />
            <span>Home chef approved</span>
          </div>
        </div>
      </div>
    </section>
  );
}

export default BecomeChefCtaSection;
