import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

function source(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), "utf8");
}

const canonicalLogoPath = "/brand/craves-logo-20260805.png";

test("customer and chef surfaces use the single shared Craves logo component", () => {
  const sharedLogoSurfaces = [
    "../components/layout/Logo.tsx",
    "../components/sections/HeroSection.tsx",
    "../components/auth/AuthModal.tsx",
    "../components/home/BrowseHeader.tsx",
    "../components/cart/CartHeader.tsx",
    "../components/checkout/CheckoutHeader.tsx",
    "../components/profile/ProfileHeader.tsx",
    "../components/tracking/TrackingHeader.tsx",
    "../screens/OrderHistory/OrderHistory.tsx",
    "../app/chef/layout.tsx",
  ];

  for (const path of sharedLogoSurfaces) {
    assert.match(source(path), /CravesLogo/, `${path} must render CravesLogo`);
  }

  const legacyLayoutLogo = source("../components/layout/Logo.tsx");
  assert.doesNotMatch(legacyLayoutLogo, /assets\/images\/craves-logo\.png/);
  assert.doesNotMatch(legacyLayoutLogo, /FOOD FROM HOME/);
});

test("canonical component, browser icons and build output use the uploaded versioned logo", () => {
  const logo = source("../components/brand/CravesLogo.tsx");
  const rootLayout = source("../app/layout.tsx");
  const packageJson = source("../../package.json");
  const packageConfig = JSON.parse(packageJson) as {
    scripts?: Record<string, string>;
  };
  const prebuild = packageConfig.scripts?.prebuild ?? "";
  const extractor = source("../../scripts/extract-brand-logo.mjs");
  const compatibilitySvg = source("../../public/brand/craves-logo.svg");

  assert.match(logo, new RegExp(canonicalLogoPath.replaceAll("/", "\\/")));
  assert.match(logo, /unoptimized/);
  assert.match(rootLayout, new RegExp(canonicalLogoPath.replaceAll("/", "\\/")));
  assert.match(prebuild, /^node scripts\/extract-brand-logo\.mjs(?: &&|$)/);
  assert.match(prebuild, /node scripts\/extract-landing-reference-assets\.mjs/);
  assert.match(extractor, /craves-logo-20260805\.base64\.00/);
  assert.match(extractor, /craves-logo-20260805\.base64\.04/);
  assert.match(extractor, /afb6751bb1291f5cba13f3223140cc42229cb00696e025f617766527d6c7fd07/);
  assert.match(extractor, /import sharp from "sharp"/);
  assert.match(extractor, /width !== 112/);
  assert.match(extractor, /channels !== 4/);
  assert.match(compatibilitySvg, /craves-logo-20260805\.png/);
  assert.doesNotMatch(compatibilitySvg, /data:image\/png;base64/);
});

test("customer address BFF targets the documented APIM collection route", () => {
  const route = source("../app/api/customer/addresses/route.ts");

  assert.match(route, /authenticatedApiFetch\(request, "\/customer\/addresses"/);
  assert.match(route, /parseCustomerAddresses/);
  assert.match(route, /parseCustomerAddress/);
  assert.match(route, /INVALID_ADDRESS_RESPONSE/);
  assert.match(route, /upstreamStatus/);
});

test("address parser preserves legacy rows and gates checkout readiness", () => {
  const contract = source("./address-contract.ts");

  assert.match(contract, /recipientName: string \| null/);
  assert.match(contract, /areaName: string \| null/);
  assert.match(contract, /postalCode: string \| null/);
  assert.match(contract, /latitude: number \| null/);
  assert.match(contract, /isDeliveryReadyAddress/);
  assert.match(contract, /active: raw\.active && deliveryReady/);
});

test("address APIM pipeline is guarded and proves GET and POST routing", () => {
  const pipeline = source(
    "../../../../azure-pipelines-customer-addresses-apim.yml",
  );

  assert.match(pipeline, /confirmConfigureCustomerAddresses/);
  assert.match(pipeline, /configure-customer-addresses-apim\.sh/);
  assert.match(pipeline, /\/api\/v1\/customer\/addresses/);
  assert.match(pipeline, /GET=\$LIST_CODE POST=\$CREATE_CODE/);
  assert.match(pipeline, /expected HTTP 401 without a token/i);
});

test("address APIM configuration reuses matching live operations safely", () => {
  const script = source(
    "../../../../scripts/apim/configure-customer-addresses-apim.sh",
  );

  assert.match(script, /az apim api operation list/);
  assert.match(script, /Reusing existing APIM operation/);
  assert.match(script, /CONFIGURED_OPERATION_IDS/);
  assert.match(script, /Multiple APIM operations already use/);
  assert.match(script, /refusing to overwrite it/);
  assert.doesNotMatch(script, /az apim api operation delete/);
});
