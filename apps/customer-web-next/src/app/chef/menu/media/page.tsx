import { ChefAccessBoundary } from "@/components/chef-access-boundary";
import { ChefMenuMediaManager } from "@/components/chef-menu-media-manager";
import { ChefPageHeader } from "@/components/chef-page-header";

export const metadata = {
  title: "Menu images and availability | Craves",
  robots: { index: false, follow: false },
};

export default function ChefMenuMediaPage() {
  return (
    <main className="mx-auto min-h-screen max-w-7xl px-4 py-6 md:px-6 md:py-8">
      <ChefPageHeader
        eyebrow="Customer-facing catalog"
        title="Images and item availability"
        description="Manage Catalog-backed availability and upload the approved image formats. The browser receives only public image URLs and never receives Blob Storage paths, keys or credentials."
      />
      <div className="mt-6">
        <ChefAccessBoundary>
          <ChefMenuMediaManager />
        </ChefAccessBoundary>
      </div>
    </main>
  );
}
