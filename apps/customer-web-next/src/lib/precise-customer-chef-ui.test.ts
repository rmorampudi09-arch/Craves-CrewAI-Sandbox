import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

function source(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}

const theme = source("../craves-theme.css");
const footer = source("../components/sections/FooterSection.tsx");
const referenceHero = source(
  "../components/sections/landing-reference/ReferenceHeroDesktop.tsx",
);
const referenceArtwork = source(
  "../components/sections/landing-reference/ReferenceArtworkSection.tsx",
);
const referenceCrop = source(
  "../components/sections/landing-reference/ReferenceImageCrop.tsx",
);
const landing = source("../screens/public/LandingPage/LandingPage.tsx");
const welcome = source("../components/home/WelcomeBanner.tsx");
const checkout = source("../screens/Checkout/Checkout.tsx");
const orders = source("../screens/OrderHistory/OrderHistory.tsx");
const cart = source("../screens/Cart/Cart.tsx");
const notifications = source("../screens/Notifications/Notifications.tsx");
const addressDialog = source("../components/checkout/CheckoutAddressDialog.tsx");
const chefActions = source("../components/chef-order-actions.tsx");
const mealPlans = source("../components/subscription-plan-browser.tsx");
const mealPlanPage = source("../app/subscriptions/plans/page.tsx");

test("shared customer and chef palette removes espresso brown", () => {
  assert.doesNotMatch(theme, /#261a15/i);
  assert.doesNotMatch(theme, /rgba\(38,\s*26,\s*21/i);
  assert.match(theme, /--color-contrast-red:\s*#c92716/i);
  assert.match(theme, /--color-flame-red:\s*#f62e18/i);
  assert.match(theme, /--color-white:\s*#ffffff/i);
  assert.match(theme, /--color-black:\s*#000000/i);
});

test("buttons use white surfaces without logo-colored borders and keep the requested hover state", () => {
  assert.match(theme, /button, \[role="tab"\]/);
  assert.match(theme, /border:\s*1px solid transparent\s*!important/);
  assert.doesNotMatch(
    theme,
    /border:\s*1px solid var\(--color-flame-red\)\s*!important/,
  );
  assert.match(theme, /background:\s*var\(--color-white\)\s*!important/);
  assert.match(theme, /color:\s*var\(--color-black\)\s*!important/);
  assert.match(theme, /background:\s*var\(--color-flame-red\)\s*!important/);
  assert.match(theme, /color:\s*var\(--color-white\)\s*!important/);
  assert.match(theme, /font-weight:\s*700\s*!important/);
});

test("landing hero uses semantic HTML, canonical logo, approved rider artwork and wired controls", () => {
  assert.match(referenceHero, /import \{ CravesLogo \}/);
  assert.match(referenceHero, /<CravesLogo size="lg" priority \/>/);
  assert.match(referenceHero, /The Taste of Home,/);
  assert.match(referenceHero, /Now Closer\./);
  assert.match(referenceHero, /Order Homemade Food/);
  assert.match(referenceHero, /Watch How It Works/);
  assert.match(referenceHero, /onOpenAuth\("login"\)/);
  assert.match(referenceHero, /onOpenLocation/);
  assert.match(referenceHero, /onBecomeChef/);
  assert.match(referenceHero, /src="\/landing\/reference\/hero-reference\.png"/);
  assert.match(referenceHero, /<ReferenceImageCrop/);
  assert.doesNotMatch(referenceHero, /referenceHotspot/);
});

test("landing precision fixes remove baked rider text, align steps and normalize chef navigation hover", () => {
  assert.match(referenceHero, /href="#become-a-chef"/);
  assert.doesNotMatch(referenceHero, /className=\{styles\.referenceNavButton\}/);
  assert.match(referenceHero, /top-\[84\.4%\]/);
  assert.match(referenceHero, /h-\[5\.2%\]/);
  assert.match(referenceHero, /w-\[7\.4%\]/);
  assert.match(referenceHero, /!min-h-0/);

  assert.match(
    referenceArtwork,
    /lg:h-\[clamp\(14rem,22vw,21rem\)\]/,
  );
  assert.match(referenceArtwork, /items-end justify-center/);
  assert.match(referenceArtwork, /referenceHowArtwork\} !m-0/);
});

test("public landing keeps the approved semantic reference experience and wired flows", () => {
  assert.match(landing, /min-h-screen bg-white text-ink/);
  assert.match(landing, /items-center justify-center bg-white px-4/);
  assert.match(landing, /<ReferenceHeroDesktop/);
  assert.match(landing, /<ReferenceArtworkSection variant="how"/);
  assert.match(landing, /<ReferenceArtworkSection variant="why"/);
  assert.match(landing, /variant="chefs-app"/);
  assert.match(landing, /<AuthModal/);
  assert.match(landing, /<LocationModal/);
  assert.doesNotMatch(landing, /<CommunityImpactSection/);
  assert.doesNotMatch(landing, /<AppDownloadSection/);

  assert.match(referenceArtwork, /From their kitchen to/);
  assert.match(referenceArtwork, /your table\./);
  assert.match(referenceArtwork, /Food the way it/);
  assert.match(referenceArtwork, /should be\./);
  assert.match(referenceArtwork, /Every order supports/);
  assert.match(referenceArtwork, /real people/);
  assert.match(referenceArtwork, /Real kitchens\./);
  assert.match(referenceArtwork, /Real people\./);
  assert.match(referenceArtwork, /Real passion\./);
  assert.match(referenceArtwork, /Homemade food,/);
  assert.match(referenceArtwork, /in your pocket\./);
  assert.match(referenceArtwork, /Become a Home Chef/);
  assert.match(referenceArtwork, /id="craves-app"/);

  assert.match(referenceCrop, /unoptimized/);
  assert.match(referenceCrop, /approved reference PNG/);
  assert.match(referenceCrop, /style=\{imageStyle\}/);

  assert.match(footer, /<CravesLogo size="lg" \/>/);
  assert.match(footer, /bg-\[#111111\] text-white/);
  assert.doesNotMatch(landing, /min-h-screen bg-cream text-ink/);
});

test("welcome banner is solid contrast red with white copy", () => {
  assert.match(welcome, /bg-\[#C92716\]/);
  assert.match(welcome, /Welcome back, \{firstName\}/);
  assert.match(welcome, /Fresh dishes available around your delivery address\./);
  assert.match(welcome, /text-white/);
  assert.doesNotMatch(welcome, /blur-|gradient|bg-primary\/25/);
});

test("meal plans keep their previous card layout and navigation flow", () => {
  assert.match(mealPlans, /meal-plans-legacy-ui/);
  assert.match(mealPlans, /rounded-\[28px\] bg-\[#FFF8EC\]/);
  assert.match(mealPlans, /subscriptions\/new\?planId=/);
  assert.match(mealPlans, /craves-button-link/);
  assert.match(mealPlanPage, /bg-\[#0B1426\]/);
});

test("checkout shows only the current address and manages all addresses in a dialog", () => {
  assert.match(checkout, /CheckoutAddressDialog/);
  assert.match(checkout, /Only the address selected for this checkout is shown here/);
  assert.match(checkout, /Manage address/);
  assert.match(checkout, /onAddressesChange=\{setAddresses\}/);
  assert.doesNotMatch(checkout, /<fieldset/);
  assert.doesNotMatch(checkout, /addresses\.map/);

  assert.match(addressDialog, /role="dialog"/);
  assert.match(addressDialog, /fetch\("\/api\/customer\/addresses"/);
  assert.match(addressDialog, /method:\s*"POST"/);
  assert.match(addressDialog, /parseCustomerAddresses/);
  assert.match(addressDialog, /Add new address/);
  assert.match(addressDialog, /Save and use this address/);
});

test("customer orders page uses a white page surface", () => {
  assert.match(orders, /min-h-screen bg-white pb-20 text-ink/);
  assert.doesNotMatch(orders, /min-h-screen bg-cream pb-20 text-ink/);
});

test("customer cart and notifications use white page surfaces", () => {
  assert.match(cart, /min-h-screen bg-white pb-32 text-ink/);
  assert.doesNotMatch(cart, /min-h-screen bg-cream pb-32 text-ink/);
  assert.match(notifications, /min-h-screen bg-white pb-12/);
  assert.match(notifications, /border-b border-border bg-white\/95/);
  assert.doesNotMatch(notifications, /min-h-screen bg-cream pb-12/);
  assert.doesNotMatch(notifications, /border-b border-border bg-cream\/95/);
});

test("chef accept and reject fields use one neutral border with no focus outline or ring", () => {
  assert.match(chefActions, /data-craves-single-border="true"/);
  assert.match(chefActions, /border border-border/);
  assert.match(chefActions, /focus:outline-none focus:ring-0/);
  assert.match(theme, /outline:\s*none\s*!important/);
  assert.match(
    theme,
    /border:\s*1px solid var\(--color-grey-200\)\s*!important/,
  );
  assert.doesNotMatch(
    theme,
    /border:\s*1px solid var\(--color-flame-red\)\s*!important/,
  );
});
