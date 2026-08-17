import {
  ChefHat,
  Heart,
  House,
  MapPin,
  Search,
  ShoppingBag,
  Sparkles,
  Sprout,
} from "lucide-react";

import styles from "@/screens/public/LandingPage/LandingV2.module.css";
import { ReferenceImageCrop } from "./ReferenceImageCrop";

type ReferenceArtworkVariant = "how" | "why" | "chefs-app";

interface ReferenceArtworkSectionProps {
  variant: ReferenceArtworkVariant;
  priority?: boolean;
  onBecomeChef?: () => void;
}

const howSteps = [
  {
    number: "01",
    title: "Discover",
    body: "Find homemade meals from home chefs near you.",
    icon: Search,
    crop: { x: 150, y: 450, width: 390, height: 390 },
    alt: "Craves mobile discovery screen showing homemade meals.",
  },
  {
    number: "02",
    title: "Order",
    body: "Choose what you’re craving and place your order.",
    icon: ShoppingBag,
    crop: { x: 565, y: 455, width: 390, height: 360 },
    alt: "Shopping basket filled with a homemade meal.",
  },
  {
    number: "03",
    title: "Chef Prepares",
    body: "Your home chef freshly prepares your meal.",
    icon: ChefHat,
    crop: { x: 1000, y: 440, width: 440, height: 390 },
    alt: "Home chef preparing a fresh meal in a red cooking pot.",
  },
  {
    number: "04",
    title: "Enjoy",
    body: "Fresh homemade food arrives at your doorstep.",
    icon: Heart,
    crop: { x: 1470, y: 430, width: 410, height: 400 },
    alt: "Craves rider delivering homemade food by scooter.",
  },
] as const;

const whyItems = [
  {
    title: "Trusted Home Chefs",
    body: "Real people. Real kitchens. Food prepared with care and personal recipes.",
    icon: House,
    crop: { x: 130, y: 415, width: 380, height: 300 },
    alt: "Hand-drawn home surrounded by plants and hearts.",
  },
  {
    title: "Made Nearby",
    body: "Discover home-cooked meals prepared by home chefs around you.",
    icon: MapPin,
    crop: { x: 570, y: 430, width: 390, height: 285 },
    alt: "Red location pin surrounded by neighbourhood homes.",
  },
  {
    title: "Freshly Prepared",
    body: "Made fresh. No mass production. No shortcuts. Just real home food.",
    icon: ChefHat,
    crop: { x: 1010, y: 420, width: 390, height: 290 },
    alt: "Steaming red cooking pot with fresh ingredients.",
  },
  {
    title: "Discover Something Different",
    body: "Explore dishes, cuisines and flavours beyond restaurant menus.",
    icon: Heart,
    crop: { x: 1470, y: 430, width: 410, height: 285 },
    alt: "Bowl of homemade food surrounded by hand-drawn hearts.",
  },
] as const;

function SectionEyebrow({ children }: { children: string }) {
  return (
    <p className={styles.referenceEyebrow}>
      {children}
      <span aria-hidden="true" />
    </p>
  );
}

function HowItWorks({ priority }: { priority: boolean }) {
  return (
    <section className={styles.referenceSemanticSection} aria-labelledby="how-craves-title">
      <div className={styles.referenceSectionInner}>
        <div className={styles.referenceCenteredHeading}>
          <SectionEyebrow>HOW CRAVES WORKS</SectionEyebrow>
          <h2 id="how-craves-title" className={styles.referenceSectionTitle}>
            From their kitchen to <span>your table.</span>
          </h2>
          <p>Simple steps. Real people. Homemade food.</p>
        </div>

        <div className={styles.referenceHowGrid}>
          {howSteps.map((step, index) => {
            const Icon = step.icon;
            return (
              <article key={step.number} className={styles.referenceHowCard}>
                <div className={styles.referenceStepNumber}>{step.number}</div>
                <div className="mb-[0.8rem] mt-[0.55rem] flex items-end justify-center lg:h-[clamp(14rem,22vw,21rem)]">
                  <ReferenceImageCrop
                    src="/landing/reference/how-craves-works-reference.png"
                    sourceWidth={2048}
                    sourceHeight={1369}
                    crop={step.crop}
                    alt={step.alt}
                    priority={priority && index < 2}
                    sizes="(min-width: 1024px) 24vw, (min-width: 640px) 48vw, 92vw"
                    className={`${styles.referenceHowArtwork} !m-0`}
                  />
                </div>
                <div className={styles.referenceStepIcon}>
                  <Icon className="h-5 w-5" strokeWidth={1.8} aria-hidden="true" />
                </div>
                <h3>{step.title}</h3>
                <p>{step.body}</p>
                {index < howSteps.length - 1 ? (
                  <span className={styles.referenceStepArrow} aria-hidden="true">→</span>
                ) : null}
              </article>
            );
          })}
        </div>

        <div className={styles.referenceLoveCallout}>
          <Heart className="h-7 w-7 fill-current" aria-hidden="true" />
          <div>
            <strong>Cooked with love. Delivered with care.</strong>
            <span>Because food made at home, tastes like home.</span>
          </div>
          <Sprout className="ml-auto hidden h-8 w-8 sm:block" aria-hidden="true" />
        </div>
      </div>
    </section>
  );
}

function WhyCraves() {
  return (
    <section className={styles.referenceSemanticSection} aria-labelledby="why-craves-title">
      <div className={styles.referenceSectionInner}>
        <div className={styles.referenceCenteredHeading}>
          <SectionEyebrow>WHY CRAVES?</SectionEyebrow>
          <h2 id="why-craves-title" className={styles.referenceSectionTitle}>
            Food the way it <span>should be.</span>
          </h2>
          <p>
            We connect you with trusted home chefs who cook with care
            <br className="hidden md:block" /> so you can enjoy real home food, every day.
          </p>
        </div>

        <div className={styles.referenceWhyGrid}>
          {whyItems.map((item) => {
            const Icon = item.icon;
            return (
              <article key={item.title} className={styles.referenceWhyCard}>
                <ReferenceImageCrop
                  src="/landing/reference/why-craves-reference.png"
                  sourceWidth={2048}
                  sourceHeight={1386}
                  crop={item.crop}
                  alt={item.alt}
                  sizes="(min-width: 1024px) 24vw, (min-width: 640px) 48vw, 92vw"
                  className={styles.referenceWhyArtwork}
                />
                <div className={styles.referenceStepIcon}>
                  <Icon className="h-5 w-5" strokeWidth={1.8} aria-hidden="true" />
                </div>
                <h3>{item.title}</h3>
                <span className={styles.referenceHeartRule} aria-hidden="true">♥</span>
                <p>{item.body}</p>
              </article>
            );
          })}
        </div>

        <div className={styles.referenceImpactCallout}>
          <Heart className="h-7 w-7 fill-current" aria-hidden="true" />
          <div>
            <strong>
              Every order supports <span>real people</span> and their <span>passion.</span>
            </strong>
            <p>Thank you for choosing homemade.</p>
          </div>
          <Sprout className="ml-auto hidden h-8 w-8 sm:block" aria-hidden="true" />
        </div>
      </div>
    </section>
  );
}

function ChefsAndApp({ onBecomeChef }: { onBecomeChef?: () => void }) {
  return (
    <section className={styles.referenceSemanticSection} aria-labelledby="home-chefs-title">
      <div className={styles.referenceSectionInner}>
        <div className={styles.referenceChefsIntro}>
          <div>
            <SectionEyebrow>MEET THE HOME CHEFS</SectionEyebrow>
            <h2 id="home-chefs-title" className={styles.referenceChefsTitle}>
              Real kitchens.<br />
              Real people.<br />
              <span>Real passion.</span>
            </h2>
            <p>
              Passionate home chefs bring their favourite recipes to your table.
              <br className="hidden sm:block" /> Made with love, just like at home.
            </p>
            {onBecomeChef ? (
              <button
                type="button"
                onClick={onBecomeChef}
                className={`${styles.referencePrimaryButton} ${styles.referenceChefButton}`}
              >
                Become a Home Chef
                <span aria-hidden="true">→</span>
              </button>
            ) : null}
          </div>
          <div className={styles.referenceChefNote} aria-hidden="true">
            <Sparkles className="h-5 w-5" />
            <span>Good food<br />Good mood<br />Made at home</span>
            <Heart className="h-5 w-5 fill-current" />
          </div>
        </div>

        <ReferenceImageCrop
          src="/landing/reference/home-chefs-app-reference.png"
          sourceWidth={2048}
          sourceHeight={1372}
          crop={{ x: 80, y: 500, width: 1880, height: 340 }}
          alt="Three home chefs preparing different homemade dishes in warm, personal kitchens."
          sizes="(min-width: 1024px) 92vw, 100vw"
          className={styles.referenceChefPanorama}
        />

        <div className={styles.referenceKitchenLine}>
          <span aria-hidden="true">♥</span>
          Different kitchens. Different recipes. One feeling — home.
          <span aria-hidden="true">♥</span>
        </div>

        <div id="craves-app" className={styles.referenceAppGrid}>
          <div className={styles.referenceAppCopy}>
            <SectionEyebrow>CRAVES APP</SectionEyebrow>
            <h2 className={styles.referenceAppTitle}>
              Homemade food,<br />
              <span>in your pocket.</span>
            </h2>
            <p>
              Discover, order and enjoy delicious homemade meals from trusted
              home chefs near you.
            </p>
            <div className={styles.referenceStoreBadges} aria-label="Craves mobile apps are coming soon">
              <span className={styles.referenceStoreBadge}>
                <small>GET IT ON</small>
                <strong>Google Play</strong>
              </span>
              <span className={styles.referenceStoreBadge}>
                <small>Download on the</small>
                <strong>App Store</strong>
              </span>
            </div>
          </div>

          <div className="relative overflow-hidden bg-white">
            <ReferenceImageCrop
              src="/landing/reference/home-chefs-app-reference.png"
              sourceWidth={2048}
              sourceHeight={1372}
              crop={{ x: 880, y: 855, width: 1010, height: 510 }}
              alt="Craves mobile app screens for discovering meals and exploring home chefs."
              sizes="(min-width: 1024px) 55vw, 100vw"
              className={styles.referenceAppArtwork}
            />
            <span
              aria-hidden="true"
              className="pointer-events-none absolute left-0 top-0 z-[3] h-[6.5%] w-[47%] bg-white"
            />
            <span
              aria-hidden="true"
              className="pointer-events-none absolute left-[46.5%] top-0 z-[3] h-[6.5%] w-[18.5%] bg-white"
            />
          </div>
        </div>
      </div>
    </section>
  );
}

export function ReferenceArtworkSection({
  variant,
  priority = false,
  onBecomeChef,
}: ReferenceArtworkSectionProps) {
  if (variant === "how") return <HowItWorks priority={priority} />;
  if (variant === "why") return <WhyCraves />;
  return <ChefsAndApp onBecomeChef={onBecomeChef} />;
}

export default ReferenceArtworkSection;
