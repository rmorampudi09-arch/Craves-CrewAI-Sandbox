import styles from "@/screens/public/LandingPage/LandingV2.module.css";

const metrics = [
  {
    value: "Growing",
    label: "Home chef community",
    note: "Live chef counts will be shown only when verified.",
  },
  {
    value: "Fresh daily",
    label: "Meals made to order",
    note: "No fabricated delivery totals on the public landing page.",
  },
  {
    value: "Hyderabad",
    label: "Launch city",
    note: "Craves is focused on Hyderabad first.",
  },
] as const;

/** Public impact strip. Numeric marketplace claims are intentionally not invented. */
export function CommunityImpactSection() {
  return (
    <section className="bg-[#111111] py-20 text-white md:py-24">
      <div className="mx-auto grid max-w-7xl gap-10 px-4 md:px-6 lg:grid-cols-[minmax(0,34rem)_1fr] lg:items-center lg:gap-14">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#F62E18]">
            Community impact
          </p>
          <h2 className="mt-4 font-display text-4xl font-bold leading-[1.08] tracking-[-0.05em] text-white md:text-5xl">
            Built around people,
            <span className="block">not production lines.</span>
          </h2>
          <p className="mt-5 max-w-lg text-sm leading-6 text-[#A7ABB0]">
            Public numbers will be added only when the marketplace can provide verified live metrics.
          </p>
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          {metrics.map((metric) => (
            <article key={metric.label} className={`${styles.darkCard} rounded-3xl p-6`}>
              <p className="font-display text-2xl font-bold tracking-[-0.04em] text-white md:text-3xl">
                {metric.value}
              </p>
              <h3 className="mt-4 text-sm font-bold text-white">{metric.label}</h3>
              <p className="mt-2 text-xs leading-5 text-[#A7ABB0]">{metric.note}</p>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

export default CommunityImpactSection;
