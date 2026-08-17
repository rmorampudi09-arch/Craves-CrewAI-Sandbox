import styles from "@/screens/public/LandingPage/LandingV2.module.css";

const sampleMeals = [
  ["Telangana home thali", "Home chef • nearby"],
  ["Chicken curry & rice", "Home chef • hearty"],
  ["Comfort dal bowl", "Home chef • everyday"],
] as const;

/** App download callout. Store links stay disabled until official listings exist. */
export function AppDownloadSection() {
  return (
    <section id="download-app" className="scroll-mt-20 bg-white py-24 md:py-28">
      <div className="mx-auto grid max-w-7xl gap-14 px-4 md:px-6 lg:grid-cols-[minmax(0,42rem)_22rem] lg:items-center lg:gap-24">
        <div>
          <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#F62E18]">
            Craves in your pocket
          </p>
          <h2 className="mt-4 font-display text-4xl font-bold leading-[1.08] tracking-[-0.05em] text-[#111111] md:text-5xl lg:text-6xl">
            Home cooked happiness,
            <span className="block">just a tap away.</span>
          </h2>
          <p className="mt-7 max-w-xl text-base leading-8 text-[#6E7378] md:text-lg">
            Browse local home chefs, discover what’s cooking today, order securely
            and follow your meal from chef to door.
          </p>

          <div className="mt-8 flex flex-wrap gap-3" aria-label="Mobile app availability">
            <button
              type="button"
              disabled
              className={`${styles.storeButton} min-h-14 px-5 text-left text-xs leading-4`}
              title="App Store listing is not published yet"
            >
              <span className="block text-[0.65rem] font-medium text-white/70">COMING SOON ON THE</span>
              <span className="mt-0.5 block text-sm font-bold">App Store</span>
            </button>
            <button
              type="button"
              disabled
              className={`${styles.storeButton} min-h-14 px-5 text-left text-xs leading-4`}
              title="Google Play listing is not published yet"
            >
              <span className="block text-[0.65rem] font-medium text-white/70">COMING SOON ON</span>
              <span className="mt-0.5 block text-sm font-bold">Google Play</span>
            </button>
          </div>
        </div>

        <div className="relative mx-auto w-full max-w-[19.5rem] rounded-[2.625rem] border-[0.5rem] border-[#111111] bg-white p-4 shadow-none">
          <div className="mx-auto h-2.5 w-24 rounded-full bg-[#111111]" aria-hidden="true" />
          <p className="mt-6 text-xs font-medium text-[#6E7378]">Good evening</p>
          <h3 className="mt-2 text-lg font-bold leading-6 text-[#111111]">
            What feels like home today?
          </h3>
          <div className="mt-5 space-y-3">
            {sampleMeals.map(([title, subtitle]) => (
              <div key={title} className="grid grid-cols-[3rem_1fr] gap-3 rounded-2xl bg-[#F6F7F8] p-3">
                <div className="h-12 w-12 rounded-xl border border-[#F62E18] bg-white" aria-hidden="true" />
                <div className="min-w-0 py-1">
                  <p className="truncate text-xs font-bold text-[#111111]">{title}</p>
                  <p className="mt-1 truncate text-[0.65rem] text-[#6E7378]">{subtitle}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}

export default AppDownloadSection;
