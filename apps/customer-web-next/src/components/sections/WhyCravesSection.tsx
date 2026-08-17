import { ChefHat, Heart } from "lucide-react";

import styles from "@/screens/public/LandingPage/LandingV2.module.css";

/** Craves mission story using clean neutral surfaces and editable icon accents only. */
export function WhyCravesSection() {
  return (
    <section className="relative isolate overflow-hidden bg-[#F6F7F8] py-24 md:py-32">
      <div
        aria-hidden="true"
        className="absolute -right-28 top-1/2 h-[34rem] w-[42rem] -translate-y-1/2 rounded-[50%] bg-white"
      />

      <div className="relative z-10 mx-auto grid max-w-7xl gap-12 px-4 md:px-6 lg:grid-cols-[minmax(0,40rem)_1fr] lg:items-center">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#F62E18]">
            Why Craves exists
          </p>
          <h2 className="mt-4 max-w-2xl font-display text-4xl font-bold leading-[1.08] tracking-[-0.05em] text-[#111111] md:text-5xl lg:text-6xl">
            Food should feel
            <span className="block">like it came from home.</span>
          </h2>
          <p className="mt-7 max-w-2xl text-base leading-8 text-[#6E7378] md:text-lg">
            Craves exists to make everyday meals more human. We connect customers
            with talented home chefs who cook with the care, familiarity and
            freshness commercial kitchens often lose at scale.
          </p>
          <p className="mt-8 max-w-xl text-lg font-bold leading-7 text-[#F62E18] md:text-xl">
            “Real people. Real recipes. No compromise on freshness.”
          </p>
        </div>

        <div className="relative hidden min-h-[22rem] lg:block" aria-hidden="true">
          <div
            className={`${styles.sticker} absolute right-12 top-10 flex rotate-3 items-center gap-3 rounded-2xl px-5 py-4 text-sm font-semibold text-[#111111]`}
          >
            <ChefHat className="h-6 w-6 text-[#F62E18]" strokeWidth={1.8} />
            <span>Real people. Real food.</span>
          </div>
          <div
            className={`${styles.sticker} absolute bottom-16 left-10 flex -rotate-3 items-center gap-3 rounded-2xl px-5 py-4 text-sm font-semibold text-[#111111]`}
          >
            <Heart className="h-6 w-6 text-[#F62E18]" strokeWidth={1.8} />
            <span>Made with care</span>
          </div>
          <span className="absolute right-24 top-44 text-5xl text-[#F62E18]">✦</span>
        </div>
      </div>
    </section>
  );
}

export default WhyCravesSection;
