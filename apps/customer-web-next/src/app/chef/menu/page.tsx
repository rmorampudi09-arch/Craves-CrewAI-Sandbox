import Link from "next/link";
import { ImageIcon } from "lucide-react";
import { ChefAccessBoundary } from "@/components/chef-access-boundary";
import { ChefMenuManager } from "@/components/chef-menu-manager";
import { ChefPageHeader } from "@/components/chef-page-header";

export const metadata = {
  title: "Chef menu | Craves",
  robots: { index: false, follow: false },
};

export default function ChefMenuPage() {
  return (
    <main className="mx-auto min-h-screen max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <ChefPageHeader
        eyebrow="Catalog menu"
        title="Dishes, prices and preparation"
        description="Create and edit only the menu records owned by your Catalog kitchen. Price, status, food type, preparation details and availability are persisted by Catalog Service."
        action={
          <Link
            href="/chef/menu/media"
            className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-white/30 px-4 text-sm font-semibold text-white hover:bg-white/10"
          >
            <ImageIcon className="h-4 w-4" aria-hidden="true" />
            Images and availability
          </Link>
        }
      />
      <div className="mt-6">
        <ChefAccessBoundary>
          <ChefMenuManager />
        </ChefAccessBoundary>
      </div>
    </main>
  );
}
