import { ChefHat, House, ShieldCheck, Truck } from "lucide-react";

const steps = [
  {
    number: "01",
    title: "Browse home chefs",
    description: "Find local cooks, regional favourites and everyday comfort meals.",
    icon: House,
  },
  {
    number: "02",
    title: "Order what you love",
    description: "Choose your meal, schedule it and place your order securely.",
    icon: ChefHat,
  },
  {
    number: "03",
    title: "Packed hygienically",
    description: "Freshly prepared and packed to Craves hygiene standards.",
    icon: ShieldCheck,
  },
  {
    number: "04",
    title: "Delivered with care",
    description: "From chef to door while it is fresh and ready to enjoy.",
    icon: Truck,
  },
] as const;

/** Four-step browse/order/pack/deliver flow, with no photographic assets. */
export function HowItWorksSection() {
  return (
    <section className="relative overflow-hidden bg-white pb-28 pt-20 md:pb-36 md:pt-28">
      <div className="mx-auto max-w-7xl px-4 md:px-6">
        <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#F62E18]">
          How it works
        </p>
        <h2 className="mt-4 max-w-2xl font-display text-4xl font-bold leading-[1.06] tracking-[-0.05em] text-[#111111] md:text-5xl lg:text-6xl">
          From a home kitchen
          <span className="block">to your table.</span>
        </h2>

        <div className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
          {steps.map((step) => (
            <article
              key={step.number}
              className="min-h-[21rem] rounded-[1.75rem] border border-[#E6E8EA] bg-white p-6"
            >
              <p className="text-xs font-bold text-[#F62E18]">{step.number}</p>
              <div className="mt-7 flex h-16 w-16 items-center justify-center rounded-full border border-[#E6E8EA] bg-white text-[#F62E18]">
                <step.icon className="h-8 w-8" strokeWidth={1.8} aria-hidden="true" />
              </div>
              <h3 className="mt-7 text-lg font-bold text-[#111111]">{step.title}</h3>
              <p className="mt-3 text-sm leading-6 text-[#6E7378]">{step.description}</p>
            </article>
          ))}
        </div>
      </div>

      <svg
        aria-hidden="true"
        className="absolute inset-x-0 bottom-0 h-20 w-full md:h-24"
        viewBox="0 0 1440 100"
        preserveAspectRatio="none"
      >
        <path
          d="M0 70c270-80 510 58 790 0 236-49 415-36 650 3v27H0V70Z"
          fill="#F6F7F8"
        />
      </svg>
    </section>
  );
}

export default HowItWorksSection;
