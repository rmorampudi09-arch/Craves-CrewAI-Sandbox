import {
  Bike,
  Heart,
  House,
  Leaf,
  MapPin,
  Menu,
  Play,
  Smartphone,
  X,
} from "lucide-react";
import { useState } from "react";

import { CravesLogo } from "@/components/brand/CravesLogo";
import styles from "@/screens/public/LandingPage/LandingV2.module.css";
import { ReferenceImageCrop } from "./ReferenceImageCrop";

interface ReferenceHeroDesktopProps {
  locationLabel: string;
  onOpenLocation: () => void;
  onOpenAuth: (mode: "login" | "register") => void;
  onOrderFood: () => void;
  onBecomeChef: () => void;
}

const navLinks = [
  { href: "#top", label: "Home" },
  { href: "#how-it-works", label: "How It Works" },
  { href: "#why-craves", label: "Why Craves" },
  { href: "#contact", label: "Contact" },
] as const;

const featureCards = [
  {
    icon: House,
    title: "Cooked by Home Chefs",
    body: "Real people, real kitchens, real care.",
  },
  {
    icon: Leaf,
    title: "Fresh & Hygienic",
    body: "Made with fresh ingredients in clean kitchens.",
  },
  {
    icon: Heart,
    title: "Made with Love",
    body: "Every meal is prepared with love and passion.",
  },
  {
    icon: Bike,
    title: "Delivered to You",
    body: "Hot, fresh and on time at your doorstep.",
  },
] as const;

export function ReferenceHeroDesktop({
  locationLabel,
  onOpenLocation,
  onOpenAuth,
  onOrderFood,
  onBecomeChef,
}: ReferenceHeroDesktopProps) {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <section id="top" className={styles.referenceSemanticHero}>
      <header className={styles.referenceHeader}>
        <div className={styles.referenceHeaderInner}>
          <a href="#top" className={styles.referenceBrand} aria-label="Craves home">
            <CravesLogo size="lg" priority />
            <span className={styles.referenceBrandTagline}>Food From Home</span>
          </a>

          <nav className={styles.referenceNav} aria-label="Landing page navigation">
            {navLinks.slice(0, 2).map((link) => (
              <a key={link.href} href={link.href} className={styles.referenceNavLink}>
                {link.label}
              </a>
            ))}
            <a
              href="#become-a-chef"
              onClick={(event) => {
                event.preventDefault();
                onBecomeChef();
              }}
              className={styles.referenceNavLink}
            >
              For Chefs
            </a>
            {navLinks.slice(2).map((link) => (
              <a key={link.href} href={link.href} className={styles.referenceNavLink}>
                {link.label}
              </a>
            ))}
          </nav>

          <a
            href="#sign-in"
            onClick={(event) => {
              event.preventDefault();
              onOpenAuth("login");
            }}
            className={`${styles.secondaryCta} hidden min-h-11 items-center justify-center px-4 text-sm font-extrabold no-underline lg:inline-flex`}
          >
            Sign up / Sign in
          </a>

          <a href="#craves-app" className={styles.referenceGetAppButton}>
            <span>Get the App</span>
            <span className={styles.referenceGetAppDivider} aria-hidden="true" />
            <Smartphone className="h-4 w-4" aria-hidden="true" />
          </a>

          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            className={styles.referenceMenuButton}
            aria-label={menuOpen ? "Close navigation" : "Open navigation"}
            aria-expanded={menuOpen}
          >
            {menuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
        </div>

        {menuOpen ? (
          <nav className={styles.referenceMobileNav} aria-label="Mobile landing navigation">
            {navLinks.slice(0, 2).map((link) => (
              <a key={link.href} href={link.href} onClick={() => setMenuOpen(false)}>
                {link.label}
              </a>
            ))}
            <a
              href="#become-a-chef"
              onClick={(event) => {
                event.preventDefault();
                setMenuOpen(false);
                onBecomeChef();
              }}
            >
              For Chefs
            </a>
            {navLinks.slice(2).map((link) => (
              <a key={link.href} href={link.href} onClick={() => setMenuOpen(false)}>
                {link.label}
              </a>
            ))}
            <a
              href="#sign-in"
              onClick={(event) => {
                event.preventDefault();
                setMenuOpen(false);
                onOpenAuth("login");
              }}
            >
              Sign up / Sign in
            </a>
            <a href="#craves-app" onClick={() => setMenuOpen(false)}>
              Get the App
            </a>
          </nav>
        ) : null}
      </header>

      <div className={styles.referenceHeroInner}>
        <div className={styles.referenceHeroCopy}>
          <div className={styles.referenceHeroPill}>
            <House className="h-4 w-4" aria-hidden="true" />
            <span>Homemade Food. Real Taste.</span>
            <Heart className="ml-auto h-4 w-4 fill-current" aria-hidden="true" />
          </div>

          <h1 className={styles.referenceHeroTitle}>
            The Taste of Home,<br />
            <span>Now Closer.</span>
          </h1>

          <p className={styles.referenceHeroBody}>
            Discover delicious homemade meals from trusted home chefs and enjoy
            real home food, delivered fresh to your doorstep.
          </p>

          <div className={styles.referenceHeroActions}>
            <button
              type="button"
              onClick={onOrderFood}
              className={styles.referencePrimaryButton}
            >
              <span>Order Homemade Food</span>
              <span aria-hidden="true">→</span>
            </button>
            <a href="#how-it-works" className={styles.referenceWatchButton}>
              <span className={styles.referencePlayCircle}>
                <Play className="h-4 w-4 fill-current" aria-hidden="true" />
              </span>
              <span>Watch How It Works</span>
            </a>
          </div>

          <button
            type="button"
            onClick={onOpenLocation}
            className={styles.referenceLocationButton}
          >
            <MapPin className="h-4 w-4" aria-hidden="true" />
            <span>{locationLabel}</span>
          </button>
        </div>

        <div className={styles.referenceHeroArtworkWrap}>
          <div className="relative w-full overflow-hidden rounded-[1.5rem] bg-white">
            <ReferenceImageCrop
              src="/landing/reference/hero-reference.png"
              sourceWidth={2048}
              sourceHeight={1368}
              crop={{ x: 760, y: 150, width: 1180, height: 880 }}
              alt="Craves delivery rider carrying homemade food through a neighbourhood."
              priority
              sizes="(min-width: 1024px) 60vw, 100vw"
              className={`${styles.referenceHeroArtwork} !min-h-0 !rounded-none`}
            />
            <span
              aria-hidden="true"
              className="pointer-events-none absolute left-0 top-[84.4%] z-[3] h-[5.2%] w-[7.4%] bg-white"
            />
          </div>
        </div>
      </div>

      <div className={styles.referenceFeatureGrid}>
        {featureCards.map(({ icon: Icon, title, body }) => (
          <article key={title} className={styles.referenceFeatureCard}>
            <div className={styles.referenceFeatureIcon}>
              <Icon className="h-7 w-7" strokeWidth={1.8} aria-hidden="true" />
            </div>
            <div>
              <h2>{title}</h2>
              <span className={styles.referenceMiniRule} aria-hidden="true" />
              <p>{body}</p>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

export default ReferenceHeroDesktop;
