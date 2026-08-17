import { ChefHat, Heart, House } from "lucide-react";

import { CravesLogo } from "@/components/brand/CravesLogo";
import styles from "@/screens/public/LandingPage/LandingV2.module.css";

const mutedItemClass = "text-sm leading-6 text-[#C7C9CC]";

export function FooterSection() {
  return (
    <footer id="contact" className="relative overflow-hidden bg-[#111111] text-white">
      <svg aria-hidden="true" className="absolute inset-x-0 top-0 h-20 w-full md:h-24" viewBox="0 0 1440 100" preserveAspectRatio="none">
        <path d="M0 0h1440v28c-270 78-520-52-790 3C392 83 230 58 0 22V0Z" fill="#FFFFFF" />
      </svg>

      <div className="relative mx-auto max-w-7xl px-4 pb-8 pt-32 md:px-6 md:pt-36">
        <div className="grid gap-10 md:grid-cols-2 lg:grid-cols-[1.25fr_0.8fr_0.8fr_0.9fr_0.8fr_1.15fr]">
          <div>
            <CravesLogo size="lg" />
            <p className="mt-5 max-w-[15rem] text-sm leading-6 text-[#C7C9CC]">Good food. Real impact. Homemade meals from real people.</p>
          </div>

          <div>
            <FooterHeading>CRAVES</FooterHeading>
            <div className="mt-5 grid gap-2.5">
              <a href="#why-craves" className={`${styles.footerLink} text-sm leading-6`}>About us</a>
              <a href="#how-it-works" className={`${styles.footerLink} text-sm leading-6`}>How it works</a>
              <a href="/products-pricing" className={`${styles.footerLink} text-sm leading-6`}>Products &amp; pricing</a>
              <a href="/contact" className={`${styles.footerLink} text-sm leading-6`}>Contact us</a>
            </div>
          </div>

          <div>
            <FooterHeading>LEGAL</FooterHeading>
            <div className="mt-5 grid gap-2.5" aria-label="Craves legal and security pages">
              <a href="/privacy" className={`${styles.footerLink} text-sm leading-6`}>Privacy policy</a>
              <a href="/terms" className={`${styles.footerLink} text-sm leading-6`}>Terms of service</a>
              <a href="/refunds-cancellations" className={`${styles.footerLink} text-sm leading-6`}>Refunds &amp; cancellations</a>
              <a href="/security" className={`${styles.footerLink} text-sm leading-6`}>Security</a>
            </div>
          </div>

          <div>
            <FooterHeading>FOR CHEFS</FooterHeading>
            <div className="mt-5 grid gap-2.5">
              <a href="#become-a-chef" className={`${styles.footerLink} text-sm leading-6`}>Become a chef</a>
              <span className={mutedItemClass}>Chef resources</span>
              <span className={mutedItemClass}>Guidelines</span>
              <span className={mutedItemClass}>Earnings</span>
              <span className={mutedItemClass}>Help center</span>
            </div>
          </div>

          <div>
            <FooterHeading>SOCIAL</FooterHeading>
            <div className="mt-5 grid gap-2.5" aria-label="Craves social channels pending public URLs">
              <span className={mutedItemClass}>Instagram</span>
              <span className={mutedItemClass}>LinkedIn</span>
              <span className={mutedItemClass}>YouTube</span>
              <span className={mutedItemClass}>WhatsApp</span>
            </div>
          </div>

          <div>
            <FooterHeading>DOWNLOAD THE APP</FooterHeading>
            <p className="mt-5 text-sm leading-6 text-[#C7C9CC]">Delicious food at your fingertips.</p>
            <a href="#craves-app" className="mt-5 inline-flex min-h-12 items-center rounded-xl border border-white/25 px-4 text-sm font-semibold text-white transition-colors hover:border-white/50">App Store • Google Play</a>
          </div>
        </div>

        <div className="mt-14 grid gap-5 sm:grid-cols-2 lg:grid-cols-[1fr_auto] lg:items-end">
          <div className="flex flex-wrap gap-3" aria-hidden="true">
            <div className={`${styles.sticker} flex -rotate-2 items-center gap-2 rounded-2xl px-4 py-3 text-xs font-semibold text-[#111111]`}>
              <House className="h-5 w-5 text-[#F62E18]" strokeWidth={1.8} /><span>Home chef approved</span>
            </div>
            <div className={`${styles.sticker} flex rotate-2 items-center gap-2 rounded-2xl px-4 py-3 text-xs font-semibold text-[#111111]`}>
              <ChefHat className="h-5 w-5 text-[#F62E18]" strokeWidth={1.8} /><span>Made with love</span>
            </div>
          </div>
          <p className="text-right text-xs font-semibold text-[#F62E18]">food from home.</p>
        </div>

        <div className="mt-10 flex flex-col gap-3 border-t border-white/10 pt-6 text-xs text-[#A7ABB0] sm:flex-row sm:items-center sm:justify-between">
          <span>© {new Date().getFullYear()} Craves. All rights reserved.</span>
          <span className="inline-flex items-center gap-1.5">Made with <Heart className="h-3.5 w-3.5 fill-[#F62E18] text-[#F62E18]" aria-hidden="true" /> in Hyderabad</span>
        </div>
      </div>
    </footer>
  );
}

function FooterHeading({ children }: { children: string }) {
  return <div><p className="text-xs font-bold tracking-[0.04em] text-white">{children}</p><span className="mt-2 block h-0.5 w-7 bg-[#F62E18]" aria-hidden="true" /></div>;
}

export default FooterSection;
