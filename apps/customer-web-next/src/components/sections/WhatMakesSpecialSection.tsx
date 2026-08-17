import { ChefHat, House, ShieldCheck, Truck } from "lucide-react";

const features = [
  {
    title: "Support Home Chefs",
    description: "Empowering home chefs and the passion they cook with.",
    icon: House,
  },
  {
    title: "Homemade Goodness",
    description: "Fresh ingredients, authentic recipes and real taste.",
    icon: ChefHat,
  },
  {
    title: "Safe & Hygienic",
    description: "Careful preparation and hygienic packing standards.",
    icon: ShieldCheck,
  },
  {
    title: "Delivered with Care",
    description: "Homemade food brought from chef to door with care.",
    icon: Truck,
  },
] as const;

/** Clean four-feature strip from the approved landing-page design. */
export function WhatMakesSpecialSection() {
  return (
    <section className="bg-[#F6F7F8] py-14 md:py-16">
      <div className="mx-auto max-w-7xl px-4 md:px-6">
        <h2 className="font-display text-3xl font-bold tracking-[-0.04em] text-[#111111] md:text-4xl">
          Real food. Real home.
        </h2>

        <div className="mt-10 grid gap-8 sm:grid-cols-2 lg:grid-cols-4 lg:gap-6">
          {features.map((feature) => (
            <article key={feature.title} className="grid grid-cols-[4.75rem_1fr] gap-4">
              <div className="flex h-[4.75rem] w-[4.75rem] items-center justify-center rounded-full border border-[#E6E8EA] bg-white text-[#F62E18]">
                <feature.icon className="h-9 w-9" strokeWidth={1.8} aria-hidden="true" />
              </div>
              <div className="pt-1">
                <h3 className="text-base font-bold text-[#111111]">{feature.title}</h3>
                <p className="mt-2 text-sm leading-6 text-[#6E7378]">{feature.description}</p>
                <span className="mt-5 block h-[3px] w-9 rounded-full bg-[#F62E18]" aria-hidden="true" />
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}

export default WhatMakesSpecialSection;
