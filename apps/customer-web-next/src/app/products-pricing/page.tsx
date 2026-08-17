import Link from "next/link";

import { publicApiFetch } from "@/lib/public-api";

export const dynamic = "force-dynamic";

export const metadata = {
  title: "Products & Pricing | Craves",
  description: "Live Craves home-chef dishes and pricing in Indian rupees.",
};

type Kitchen = {
  id: string;
  kitchenName: string;
  displayName?: string | null;
  areaName?: string | null;
  city?: string | null;
};

type KitchenDiscovery = {
  kitchens?: Kitchen[];
};

type MenuItem = {
  id: string;
  itemName: string;
  description?: string | null;
  category?: string | null;
  foodType?: string | null;
  price: number;
  currency: string;
  available: boolean;
  status: string;
};

type DisplayItem = MenuItem & {
  kitchenName: string;
  location: string;
};

function money(amount: number, currency: string): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(amount);
}

async function liveMenuItems(): Promise<DisplayItem[]> {
  try {
    const kitchensResponse = await publicApiFetch("/catalog/kitchens?city=Hyderabad", {}, 8_000);
    if (!kitchensResponse.ok) return [];
    const discovery = (await kitchensResponse.json()) as KitchenDiscovery;
    const kitchens = Array.isArray(discovery.kitchens) ? discovery.kitchens.slice(0, 6) : [];
    const menus = await Promise.all(
      kitchens.map(async (kitchen) => {
        try {
          const response = await publicApiFetch(`/catalog/kitchens/${encodeURIComponent(kitchen.id)}/menu-items`, {}, 8_000);
          if (!response.ok) return [] as DisplayItem[];
          const raw = (await response.json()) as MenuItem[];
          if (!Array.isArray(raw)) return [] as DisplayItem[];
          return raw
            .filter(
              (item) =>
                item &&
                item.available === true &&
                item.status === "ACTIVE" &&
                item.currency === "INR" &&
                Number.isFinite(Number(item.price)) &&
                Number(item.price) > 0,
            )
            .map((item) => ({
              ...item,
              price: Number(item.price),
              kitchenName: kitchen.displayName || kitchen.kitchenName,
              location: [kitchen.areaName, kitchen.city].filter(Boolean).join(", "),
            }));
        } catch {
          return [] as DisplayItem[];
        }
      }),
    );
    return menus.flat().slice(0, 18);
  } catch {
    return [];
  }
}

export default async function ProductsPricingPage() {
  const items = await liveMenuItems();
  const pricingReady = items.length > 0;

  return (
    <main className="min-h-screen bg-white text-[#2B1A12]">
      <header className="border-b border-black/5">
        <div className="mx-auto flex min-h-20 max-w-6xl items-center justify-between px-4 md:px-6">
          <Link href="/" className="font-display text-2xl font-black tracking-[-0.04em] text-[#F62E18]">CRAVES</Link>
          <Link href="/discover" className="rounded-full bg-[#F62E18] px-5 py-2.5 text-sm font-bold text-white">
            Discover near me
          </Link>
        </div>
      </header>

      <section className="mx-auto max-w-6xl px-4 py-12 md:px-6 md:py-16">
        <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#F62E18]">Products &amp; pricing</p>
        <h1 className="mt-3 max-w-3xl font-display text-4xl font-black tracking-[-0.04em] md:text-5xl">
          Real home-chef dishes. Live prices in Indian rupees.
        </h1>
        <p className="mt-5 max-w-3xl text-base leading-7 text-black/65">
          Craves is a marketplace, so each home chef controls the dishes currently available through their approved kitchen. The cards below are loaded from the live Craves public catalog and show the current listed dish price in INR. Final checkout shows the complete payable amount before payment.
        </p>

        <div
          data-craves-live-pricing-status={pricingReady ? "ready" : "unavailable"}
          className="mt-10"
        >
          {pricingReady ? (
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {items.map((item) => (
                <article key={item.id} className="rounded-3xl border border-black/10 bg-white p-6 shadow-sm">
                  <p className="text-xs font-bold uppercase tracking-[0.12em] text-[#F62E18]">
                    {(item.category || "Home-cooked food").replaceAll("_", " ")}
                  </p>
                  <h2 className="mt-2 font-display text-2xl font-bold">{item.itemName}</h2>
                  <p className="mt-2 text-sm leading-6 text-black/60">
                    {item.description || "Prepared by an approved Craves home chef."}
                  </p>
                  <div className="mt-5 flex items-end justify-between gap-4 border-t border-black/5 pt-4">
                    <div>
                      <p className="text-sm font-semibold">{item.kitchenName}</p>
                      {item.location ? <p className="mt-1 text-xs text-black/50">{item.location}</p> : null}
                    </div>
                    <strong className="text-xl text-[#F62E18]">{money(item.price, item.currency)}</strong>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <div className="rounded-3xl border border-[#F62E18]/20 bg-[#FFF5F3] p-6">
              <h2 className="font-display text-2xl font-bold">Live pricing is temporarily unavailable.</h2>
              <p className="mt-2 max-w-2xl text-sm leading-6 text-black/65">
                Craves does not publish invented or placeholder prices. Production payment activation is blocked until at least one real, active INR-priced dish is available through the public catalog.
              </p>
            </div>
          )}
        </div>

        <div className="mt-10 rounded-3xl bg-[#111111] p-7 text-white">
          <h2 className="font-display text-2xl font-bold">How the final price is confirmed</h2>
          <p className="mt-3 max-w-3xl text-sm leading-6 text-white/70">
            Browse the live marketplace, add available dishes to your cart, and review checkout before paying. Craves creates the Razorpay payment order from the server-side checkout total; the customer client cannot choose a different payable amount.
          </p>
        </div>
      </section>
    </main>
  );
}
