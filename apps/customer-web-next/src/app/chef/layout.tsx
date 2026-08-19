import Link from "next/link";
import type { ReactNode } from "react";
import { redirect } from "next/navigation";
import { ArrowLeftRight, ChefHat } from "lucide-react";
import { CravesLogo } from "@/components/brand/CravesLogo";
import { ChefWorkspaceNavigation } from "@/components/chef-workspace-navigation";
import { apiBaseUrl } from "@/lib/server-api";

async function requireChefAccess(): Promise<void> {
  const response = await fetch(`${apiBaseUrl()}/auth/me`, {
    cache: "no-store",
    headers: { Accept: "application/json" },
  }).catch(() => null);

  if (!response?.ok) {
    redirect("/sign-in?next=/chef");
  }

  const payload = (await response.json().catch(() => null)) as
    | { identity?: { roles?: string[] } }
    | null;

  const roles = payload?.identity?.roles ?? [];
  const normalizedRoles = roles.map((role) => role.toUpperCase());
  if (!normalizedRoles.includes("CHEF") && !normalizedRoles.includes("ADMIN")) {
    redirect("/home");
  }
}

export default async function ChefLayout({
  children,
}: Readonly<{ children: ReactNode }>) {
  await requireChefAccess();

  return (
    <div className="chef-panel-theme">
      <header className="chef-panel-header">
        <div className="chef-panel-header-inner !max-w-7xl">
          <Link
            href="/chef"
            className="chef-panel-brand-group min-h-11 rounded-lg"
            aria-label="Craves chef workspace home"
          >
            <CravesLogo size="sm" />
            <span className="hidden sm:block">
              <span className="chef-panel-brand !block !text-lg">Craves</span>
              <span className="block text-[0.62rem] font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                Chef workspace
              </span>
            </span>
            <span className="chef-panel-mode-badge hidden md:inline-flex">
              <ChefHat className="mr-1 h-3.5 w-3.5" aria-hidden="true" />
              Chef mode
            </span>
          </Link>

          <div className="min-w-0 flex-1 overflow-hidden">
            <ChefWorkspaceNavigation />
          </div>

          <Link
            href="/home"
            className="chef-panel-customer-link inline-flex min-h-11 items-center gap-2"
          >
            <ArrowLeftRight className="h-4 w-4" aria-hidden="true" />
            <span className="hidden lg:inline">Customer mode</span>
          </Link>
        </div>
      </header>

      <div className="chef-panel-content">{children}</div>

      <footer className="chef-panel-footer !max-w-7xl">
        <p>Craves chef workspace · Role and ownership checked by every backend service</p>
        <Link href="/home">Return to customer experience</Link>
      </footer>
    </div>
  );
}
