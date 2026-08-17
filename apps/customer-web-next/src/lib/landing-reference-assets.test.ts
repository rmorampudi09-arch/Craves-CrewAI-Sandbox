import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

function source(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}

test("semantic landing hero keeps the canonical Craves logo and approved rider artwork", () => {
  const hero = source(
    "../components/sections/landing-reference/ReferenceHeroDesktop.tsx",
  );

  assert.match(
    hero,
    /import \{ CravesLogo \} from "@\/components\/brand\/CravesLogo"/,
  );
  assert.match(hero, /<CravesLogo/);
  assert.match(hero, /ReferenceImageCrop/);
  assert.match(hero, /\/landing\/reference\/hero-reference\.png/);
  assert.match(hero, /The Taste of Home/);
  assert.match(hero, /Order Homemade Food/);
  assert.match(hero, /onOpenLocation/);
  assert.doesNotMatch(hero, /referenceHotspot/);
});

test("reference sections use native copy with the three approved source images", () => {
  const artwork = source(
    "../components/sections/landing-reference/ReferenceArtworkSection.tsx",
  );

  assert.match(artwork, /ReferenceImageCrop/);
  assert.match(artwork, /From their kitchen to/);
  assert.match(artwork, /Food the way it/);
  assert.match(artwork, /Real kitchens/);
  assert.match(artwork, /Homemade food/);
  assert.match(
    artwork,
    /\/landing\/reference\/how-craves-works-reference\.png/,
  );
  assert.match(artwork, /\/landing\/reference\/why-craves-reference\.png/);
  assert.match(
    artwork,
    /\/landing\/reference\/home-chefs-app-reference\.png/,
  );
});

test("landing uses one responsive semantic implementation instead of screenshot desktop plus mobile fallback", () => {
  const landing = source("../screens/public/LandingPage/LandingPage.tsx");

  assert.match(landing, /<ReferenceHeroDesktop/);
  assert.match(landing, /<ReferenceArtworkSection variant="how"/);
  assert.match(landing, /<ReferenceArtworkSection variant="why"/);
  assert.match(landing, /variant="chefs-app"/);
  assert.doesNotMatch(landing, /className="lg:hidden"/);
  assert.doesNotMatch(landing, /<HeroSection/);
  assert.doesNotMatch(landing, /<HowItWorksSection/);
});

test("approved artwork renderer clips source pixels without generating or editing assets", () => {
  const crop = source(
    "../components/sections/landing-reference/ReferenceImageCrop.tsx",
  );
  const css = source("../screens/public/LandingPage/LandingV2.module.css");

  assert.match(crop, /styles\.referenceCrop/);
  assert.match(crop, /sourceWidth \/ crop\.width/);
  assert.match(crop, /crop\.x \/ crop\.width/);
  assert.match(crop, /crop\.y \/ crop\.height/);
  assert.match(css, /\.referenceCrop\s*\{[^}]*overflow:\s*hidden/s);
  assert.doesNotMatch(crop, /canvas/i);
  assert.doesNotMatch(crop, /filter:/i);
});

test("approved landing reference is vendored and verified without a network dependency", () => {
  const script = source("../../scripts/extract-landing-reference-assets.mjs");

  assert.match(script, /readFile/);
  assert.doesNotMatch(script, /https?:\/\//);
  assert.doesNotMatch(script, /\bfetch\s*\(/);

  assert.match(script, /hero-reference\.png/);
  assert.match(script, /how-craves-works-reference\.png/);
  assert.match(script, /why-craves-reference\.png/);
  assert.match(script, /home-chefs-app-reference\.png/);

  assert.match(
    script,
    /51d8f9f7e8fa852fcf0db35f1b52a6b8303e0ea869fb890855cb35156fa68655/,
  );
  assert.match(
    script,
    /3e149fda7da24782a129bacdad7d652aae07358e90fd15182ccdf9730aff4796/,
  );
  assert.match(
    script,
    /94592a5ac7a5ed5d4466e8ab6104bae0a062f6fb870eafb786ee36692d56f400/,
  );
  assert.match(
    script,
    /b3331ae6d53b85ba5face0383b82e25420527e208fcedee36c04a12eb19aa9bd/,
  );
});
