import type { Metadata } from "next";
import type { ReactNode } from "react";
import { redirect } from "next/navigation";
import "@syncfusion/ej2-tailwind3-theme/styles/base/base.css";
import "@syncfusion/ej2-tailwind3-theme/styles/grid/grid.css";
import "@syncfusion/ej2-tailwind3-theme/styles/pager/pager.css";
import "@syncfusion/ej2-tailwind3-theme/styles/popup/popup.css";
import "@syncfusion/ej2-tailwind3-theme/styles/spinner/spinner.css";
import "@syncfusion/ej2-tailwind3-theme/styles/tooltip/tooltip.css";
import { AdminWorkspace } from "@/components/admin-workspace";
import { apiBaseUrl } from "@/lib/server-api";

export const metadata: Metadata = {
  title: "Craves administration",
  robots: { index: false, follow: false },
};

async function requireAdminAccess(): Promise<void> {
  const response = await fetch(`${apiBaseUrl()}/auth/me`, {
    cache: "no-store",
    headers: { Accept: "application/json" },
  }).catch(() => null);

  if (!response?.ok) {
    redirect("/sign-in?next=/admin");
  }

  const payload = (await response.json().catch(() => null)) as
    | { identity?: { roles?: string[] } }
    | null;

  const roles = payload?.identity?.roles ?? [];
  if (!roles.some((role) => role.toUpperCase() === "ADMIN")) {
    redirect("/home");
  }
}

export default async function AdminLayout({
  children,
}: Readonly<{ children: ReactNode }>) {
  await requireAdminAccess();
  return <AdminWorkspace>{children}</AdminWorkspace>;
}
