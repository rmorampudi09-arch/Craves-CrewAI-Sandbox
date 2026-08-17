import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

function supportPhoneConfigured(): boolean {
  const digits = (process.env.CRAVES_PUBLIC_SUPPORT_PHONE ?? "").replace(/\D/g, "");
  return digits.length >= 10 && digits.length <= 13;
}

function businessNameConfigured(): boolean {
  return (process.env.CRAVES_REGISTERED_BUSINESS_NAME?.trim().length ?? 0) >= 2;
}

export function GET() {
  const razorpayMode = process.env.NEXT_PUBLIC_RAZORPAY_MODE ?? "unset";
  const supportPhoneReady = supportPhoneConfigured();
  const businessNameReady = businessNameConfigured();
  const response = {
    service: "craves-customer-web",
    razorpayMode,
    productionEligible: razorpayMode === "production" && supportPhoneReady && businessNameReady,
    legalVersion: "2026-08-15",
    legalPages: {
      contact: "/contact",
      terms: "/terms",
      refundsAndCancellations: "/refunds-cancellations",
      privacy: "/privacy",
      security: "/security",
      liveProductsAndPricing: "/products-pricing",
    },
    policies: {
      termsPublished: true,
      refundsAndCancellationsPublished: true,
      privacyPublished: true,
      contactPublished: true,
      productsAndPricingRoutePublished: true,
    },
    merchantIdentity: {
      publicSupportPhoneConfigured: supportPhoneReady,
      registeredBusinessNameConfigured: businessNameReady,
    },
  };

  return NextResponse.json(response, {
    headers: {
      "Cache-Control": "no-store, max-age=0",
      "X-Content-Type-Options": "nosniff",
    },
  });
}
