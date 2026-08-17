import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

function source(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}

const proxiedMutationRoutes = [
  "../app/api/chef/application/route.ts",
  "../app/api/chef/application/proof-files/route.ts",
  "../app/api/chef/kitchen/route.ts",
  "../app/api/chef/menu/route.ts",
  "../app/api/chef/menu/[menuItemId]/route.ts",
  "../app/api/chef/menu/[menuItemId]/availability/route.ts",
  "../app/api/chef/menu/[menuItemId]/images/route.ts",
  "../app/api/chef/orders/[orderId]/accept/route.ts",
  "../app/api/chef/orders/[orderId]/reject/route.ts",
  "../app/api/chef/orders/[orderId]/ready-for-pickup/route.ts",
  "../app/api/notifications/[noticeId]/read/route.ts",
];

test("all proxied chef mutations use the shared origin guard", () => {
  for (const route of proxiedMutationRoutes) {
    const contents = source(route);
    assert.match(contents, /from "@\/lib\/request-security"/, route);
    assert.match(contents, /isSameOrigin\(request\)/, route);
    assert.doesNotMatch(contents, /function sameOrigin\(/, route);
  }
});

test("authentication asks for customer or chef mode", () => {
  const contents = source("../components/auth/AuthModal.tsx");
  assert.match(contents, /Home Chef/);
  assert.match(contents, /accountMode === "chef"/);
  assert.match(contents, /onAuthenticated\?\.\(user, accountMode\)/);
});

test("signed-in home loads backend address, cart, and nearby kitchens before menu items", () => {
  const contents = source("../screens/public/BrowseFoods/BrowseFoods.tsx");
  assert.match(contents, /loadSelectedAddress\(\)/);
  assert.match(contents, /loadCart\(\)/);
  assert.match(
    contents,
    /discoverKitchens\(activeAddress\.lat, activeAddress\.lng, 5_000\)/,
  );
  assert.match(contents, /loadKitchenMenu\(kitchen\.id\)/);
  assert.match(contents, /<KitchensGrid/);
  assert.match(contents, /selectedKitchen \?/);
  assert.doesNotMatch(
    contents,
    /discoverDishes\(activeAddress\.lat, activeAddress\.lng, 5_000\)/,
  );
  assert.doesNotMatch(contents, /17\.4483|78\.3915/);
});

test("profile exposes backend chef application status", () => {
  const contents = source("../screens/Profile/Profile.tsx");
  assert.match(contents, /fetch\("\/api\/chef\/application"/);
  assert.match(contents, /Chef application pending/);
  assert.match(contents, /Become a home chef/);
});

test("production catalogue has no demo dish fallback", () => {
  const contents = source("../services/api/dishes.ts");
  assert.doesNotMatch(contents, /export const DISHES/);
  assert.doesNotMatch(contents, /NEXT_PUBLIC_CRAVES_ALLOW_CATALOG_FALLBACK/);
  assert.match(contents, /parseMenuDiscovery\(body\)/);
  assert.match(contents, /\/api\/discovery\/menu-items/);
});

test("empty nearby discovery expands without changing checkout serviceability", () => {
  const dishes = source("../services/api/dishes.ts");
  const policy = source("./catalog-discovery-policy.ts");
  assert.match(dishes, /candidateDiscoveryRadii\(radiusMeters\)/);
  assert.match(
    dishes,
    /if \(discoveredDishes\.length > 0\) return \[\.\.\.discoveredDishes\]/,
  );
  assert.match(policy, /15_000/);
  assert.match(policy, /MAX_DISCOVERY_RADIUS_METERS = 50_000/);
});

test("real backend chefs remain available in production", () => {
  const contents = source("../services/api/chefs.ts");
  assert.match(contents, /dish\.kitchenId === id/);
  assert.match(contents, /catalogBacked: true/);
  assert.doesNotMatch(contents, /reviewPool|LOCATIONS|NEXT_PUBLIC_CRAVES_ALLOW_CATALOG_FALLBACK/);
});

test("dish and chef detail pages recover live data after a browser refresh", () => {
  const dishPage = source("../screens/public/FoodDetails/FoodDetails.tsx");
  const dishService = source("../services/api/dishes.ts");
  const chef = source("../screens/public/ChefProfile/ChefProfile.tsx");

  assert.match(dishPage, /loadDish\(id\)/);
  assert.match(dishService, /\/api\/catalog\/menu-items/);
  assert.match(chef, /loadSelectedAddress\(\)/);
  assert.match(chef, /discoverDishes\(address\.lat, address\.lng\)/);
});

test("every home-chef call to action opens the live chef registration flow", () => {
  const landing = source("../screens/public/LandingPage/LandingPage.tsx");
  const hero = source("../components/sections/HeroSection.tsx");
  const application = source("../components/chef-application-workspace.tsx");
  const kitchen = source("../components/chef-kitchen-form.tsx");

  assert.match(
    landing,
    /onBecomeChef=\{\(\) => openAuth\("register", "chef", true\)\}/,
  );
  assert.match(hero, /onClick=\{onBecomeChef\}/);
  assert.match(
    landing,
    /hasChefRole\(authenticatedUser\)\s*\?\s*"\/chef"\s*:\s*"\/chef\/application"/s,
  );
  assert.match(application, /fetch\("\/api\/customer\/profile"/);
  assert.match(application, /fetch\("\/api\/customer\/addresses"/);
  assert.match(kitchen, /application\.status !== "APPROVED"/);
  assert.match(
    kitchen,
    /Use current location before activating this kitchen/,
  );
});

test("pending chef applications remain editable exactly as the backend permits", () => {
  const contents = source("../components/chef-application-workspace.tsx");
  assert.match(contents, /const locked = application\?\.status === "APPROVED"/);
  assert.match(contents, /onSubmit=\{submit\}/);
  assert.match(contents, /Update pending application/);
  assert.doesNotMatch(
    contents,
    /application\?\.status === "PENDING" \|\| application\?\.status === "APPROVED"/,
  );
});

test("chef dashboard reuses the working Craves session for applicants and chefs", () => {
  const dashboard = source("../components/chef-mode-dashboard.tsx");
  const phoneAuth = source("../components/phone-auth-form.tsx");
  assert.match(dashboard, /loadSession\(\)/);
  assert.match(dashboard, /state === "applicant"/);
  assert.match(dashboard, /Open chef application/);
  assert.doesNotMatch(dashboard, /fetch\("\/api\/chef\/me"/);
  assert.match(phoneAuth, /Secure Craves access/);
  assert.doesNotMatch(phoneAuth, /Secure customer access/);
});

test("chef identity BFF unwraps the Spring Auth Service response", () => {
  const contents = source("../app/api/chef/me/route.ts");
  assert.match(contents, /parseChefModeIdentity\(raw\?\.identity\)/);
  assert.doesNotMatch(contents, /parseChefModeIdentity\(await upstream\.json/);
});

test("protected chef pages synchronize the JWT after admin grants CHEF", () => {
  const auth = source("../services/auth/cravesAuth.ts");
  const boundary = source("../components/chef-access-boundary.tsx");
  assert.match(auth, /synchronizeSessionRoles/);
  assert.match(auth, /fetch\("\/api\/auth\/refresh"/);
  assert.match(boundary, /loadSession\(\)/);
  assert.match(boundary, /synchronizeSessionRoles\(\)/);
  assert.match(boundary, /setState\("not-approved"\)/);

  for (const page of [
    "../app/chef/kitchen/page.tsx",
    "../app/chef/menu/page.tsx",
    "../app/chef/menu/media/page.tsx",
    "../app/chef/orders/page.tsx",
    "../app/chef/orders/[orderId]/page.tsx",
  ]) {
    assert.match(source(page), /ChefAccessBoundary/, page);
  }
});

test("customer navigation stays inside customer page headers instead of above the website", () => {
  const navigation = source(
    "../components/navigation/PersistentCustomerServiceNav.tsx",
  );
  const layout = source("../app/layout.tsx");
  const homeHeader = source("../components/home/BrowseHeader.tsx");

  for (const route of [
    "/home",
    "/orders",
    "/subscriptions",
    "/notifications",
    "/chef",
  ]) {
    assert.match(navigation, new RegExp(route.replace("/", "\\/")));
  }

  assert.doesNotMatch(layout, /PersistentCustomerServiceNav/);
  assert.match(homeHeader, /lg:grid-cols-\[minmax\(18rem,1fr\)_auto\]/);
  assert.match(homeHeader, /<PersistentCustomerServiceNav/);

  for (const customerSurface of [
    "../screens/OrderHistory/OrderHistory.tsx",
    "../screens/Notifications/Notifications.tsx",
    "../app/subscriptions/page.tsx",
    "../components/profile/ProfileHeader.tsx",
    "../components/cart/CartHeader.tsx",
    "../components/checkout/CheckoutHeader.tsx",
    "../components/tracking/TrackingHeader.tsx",
    "../screens/public/FoodDetails/FoodDetails.tsx",
    "../screens/public/ChefProfile/ChefProfile.tsx",
    "../screens/OrderSuccess/OrderSuccess.tsx",
  ]) {
    assert.match(source(customerSurface), /PersistentCustomerServiceNav/, customerSurface);
  }

  assert.match(navigation, /data-customer-service-navigation="embedded"/);
  assert.match(navigation, /border-\[#F62E18\] bg-white/);
  assert.match(navigation, /text-black/);
  assert.match(navigation, /hover:bg-\[#F62E18\]/);
  assert.match(navigation, /hover:font-bold/);
  assert.match(navigation, /hover:text-white/);
  assert.doesNotMatch(navigation, /bg-\[#C92716\]/);
});
