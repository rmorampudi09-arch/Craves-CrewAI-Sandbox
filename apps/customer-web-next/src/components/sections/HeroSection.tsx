import {
  ChefHat,
  Heart,
  House,
  MapPin,
  Menu,
  Sparkles,
  Truck,
  X,
} from "lucide-react";
import { useState } from "react";

import { CravesLogo } from "@/components/brand/CravesLogo";
import styles from "@/screens/public/LandingPage/LandingV2.module.css";

interface HeroSectionProps {
  locationLabel: string;
  onOpenLocation: () => void;
  onOpenAuth: (mode: "login" | "register") => void;
  onBecomeChef: () => void;
}

const links = [
  { href: "#how-it-works", label: "How it works" },
  { href: "#why-craves", label: "Our mission" },
  { href: "#become-a-chef", label: "For chefs" },
] as const;

function Sticker({
  className,
  icon: Icon,
  label,
}: {
  className: string;
  icon: typeof Heart;
  label: string;
}) {
  return (
    <div
      aria-hidden="true"
      className={`${styles.sticker} absolute hidden items-center gap-2 rounded-2xl px-4 py-3 text-xs font-semibold text-[#111111] lg:flex ${className}`}
    >
      <Icon className="h-5 w-5 text-[#F62E18]" strokeWidth={1.8} />
      <span>{label}</span>
    </div>
  );
}

/** Public landing hero. Authentication and location flows remain backend-connected. */
export function HeroSection({
  locationLabel,
  onOpenLocation,
  onOpenAuth,
  onBecomeChef,
}: HeroSectionProps) {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <section className="relative isolate overflow-hidden bg-white text-[#111111]">
      <header className="relative z-40 border-b border-[#E6E8EA] bg-white/95 backdrop-blur-xl">
        <div className="mx-auto flex min-h-[5.75rem] max-w-7xl items-center gap-4 px-4 md:px-6">
          <a href="#top" aria-label="Craves home" className="shrink-0">
            <CravesLogo size="md" priority />
          </a>

          <nav
            className="ml-auto hidden items-center gap-8 lg:flex"
            aria-label="Public navigation"
          >
            {links.map((link) => (
              <a
                key={link.href}
                href={link.href}
                className="text-sm font-semibold text-[#111111] transition-colors hover:text-[#F62E18]"
              >
                {link.label}
              </a>
            ))}
          </nav>

          <div className="ml-auto hidden items-center gap-2 sm:flex lg:ml-5">
            <a
              href="#download-app"
              className={`${styles.secondaryCta} inline-flex min-h-11 items-center justify-center px-5 text-sm font-semibold`}
            >
              Get the app
            </a>
            <button
              type="button"
              onClick={() => onOpenAuth("login")}
              className={`${styles.textButton} min-h-11 px-3 text-sm font-semibold`}
            >
              Sign in
            </button>
            <button
              type="button"
              onClick={() => onOpenAuth("register")}
              className={`${styles.primaryCta} min-h-11 px-5 text-sm font-semibold`}
            >
              Order now
            </button>
          </div>

          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            className={`${styles.secondaryCta} ml-auto flex h-11 w-11 items-center justify-center p-0 sm:hidden`}
            aria-label={menuOpen ? "Close navigation" : "Open navigation"}
            aria-expanded={menuOpen}
          >
            {menuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </div>

        {menuOpen && (
          <nav
            className="border-t border-[#E6E8EA] bg-white px-4 py-4 sm:hidden"
            aria-label="Mobile public navigation"
          >
            <div className="mx-auto grid max-w-7xl gap-2">
              {links.map((link) => (
                <a
                  key={link.href}
                  href={link.href}
                  onClick={() => setMenuOpen(false)}
                  className="rounded-xl px-3 py-3 text-sm font-semibold text-[#111111] hover:bg-[#F6F7F8]"
                >
                  {link.label}
                </a>
              ))}
              <a
                href="#download-app"
                onClick={() => setMenuOpen(false)}
                className="rounded-xl px-3 py-3 text-sm font-semibold text-[#111111] hover:bg-[#F6F7F8]"
              >
                Get the app
              </a>
              <div className="mt-2 grid grid-cols-2 gap-3">
                <button
                  type="button"
                  onClick={() => {
                    setMenuOpen(false);
                    onOpenAuth("login");
                  }}
                  className={`${styles.secondaryCta} min-h-11 text-sm font-semibold`}
                >
                  Sign in
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setMenuOpen(false);
                    onOpenAuth("register");
                  }}
                  className={`${styles.primaryCta} min-h-11 text-sm font-semibold`}
                >
                  Order now
                </button>
              </div>
            </div>
          </nav>
        )}
      </header>

      <div id="top" className="relative min-h-[46rem] overflow-hidden bg-white">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute left-1/2 top-[9rem] -translate-x-1/2 select-none whitespace-nowrap font-display text-[clamp(8rem,22vw,20rem)] font-bold leading-none tracking-[-0.08em] text-[#F62E18]/[0.04]"
        >
          craves
        </div>

        <div
          aria-hidden="true"
          className="absolute right-[5%] top-24 hidden grid-cols-3 gap-3 lg:grid"
        >
          {Array.from({ length: 9 }).map((_, index) => (
            <span key={index} className="h-1.5 w-1.5 rounded-full bg-[#F62E18]/70" />
          ))}
        </div>
        <Sparkles
          aria-hidden="true"
          className="absolute right-[13%] top-44 hidden h-8 w-8 text-[#F62E18] lg:block"
        />

        <Sticker
          className="right-[18%] top-20 -rotate-3"
          icon={ChefHat}
          label="Made with love"
        />
        <Sticker
          className="right-[10%] top-[31rem] rotate-3"
          icon={Truck}
          label="From chef to door"
        />
        <Sticker
          className="right-[23%] top-[24rem] -rotate-2"
          icon={House}
          label="Home cooked happiness"
        />

        <div className="relative z-10 mx-auto flex min-h-[40rem] max-w-7xl items-center px-4 pb-32 pt-16 md:px-6 lg:pb-36 lg:pt-20">
          <div className="max-w-2xl">
            <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#F62E18]">
              Hyderabad • food from home
            </p>
            <h1 className="mt-5 font-display text-5xl font-bold leading-[1.02] tracking-[-0.055em] text-[#111111] md:text-7xl lg:text-[5rem]">
              Good food.
              <span className="block">Real impact.</span>
            </h1>
            <p className="mt-7 max-w-xl text-base leading-7 text-[#6E7378] md:text-xl md:leading-8">
              Every order supports a home chef and their family. Discover real,
              home-cooked meals made by people in your community — fresh,
              personal and delivered with care.
            </p>

            <div className="mt-8 flex flex-wrap gap-3">
              <button
                type="button"
                onClick={() => onOpenAuth("login")}
                className={`${styles.primaryCta} min-h-12 px-6 text-sm font-semibold`}
              >
                Explore home chefs
              </button>
              <button
                type="button"
                onClick={onBecomeChef}
                className={`${styles.secondaryCta} min-h-12 px-6 text-sm font-semibold`}
              >
                Become a chef
              </button>
            </div>

            <button
              type="button"
              onClick={onOpenLocation}
              className={`${styles.secondaryCta} mt-5 inline-flex min-h-11 max-w-full items-center gap-3 px-4 text-left text-sm`}
            >
              <MapPin className="h-4 w-4 shrink-0 text-[#F62E18]" aria-hidden="true" />
              <span className="min-w-0 truncate">
                Delivery location: <span className="font-semibold">{locationLabel}</span>
              </span>
            </button>

            <p className="mt-5 text-xs font-medium text-[#6E7378]">
              Made with love&nbsp;&nbsp;•&nbsp;&nbsp;From chef to door&nbsp;&nbsp;•&nbsp;&nbsp;Home cooked happiness
            </p>
          </div>
        </div>

        <svg
          aria-hidden="true"
          className="absolute inset-x-0 bottom-0 h-28 w-full"
          viewBox="0 0 1440 120"
          preserveAspectRatio="none"
        >
          <path
            d="M0 46C250 118 410 8 690 54c290 48 430 15 750-28v94H0V46Z"
            fill="#F6F7F8"
          />
        </svg>
      </div>
    </section>
  );
}

export default HeroSection;
