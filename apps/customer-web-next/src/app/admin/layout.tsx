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
import { authenticatedApiFetchFromServer, SessionRequiredError } from "@/lib/server-api";
import { parseIdentity } from "@/lib/auth-contract";

export const metadata: Metadata = {
  title: "Craves administration",
  robots: { index: false, follow: false },
};

async function requireAdminAccess(): Promise<void> {
  try {
    const response = await authenticatedApiFetchFromServer("/auth/me");
    const body = (await response.json().catch(() => null)) as { identity?: unknown } | null;
    const identity = parseIdentity(body?.identity);

    if (!response.ok || !identity) {
      redirect("/sign-in?next=/admin");
    }

    const hasAdminRole = identity.roles.some((role) => role.toUpperCase() === "ADMIN");
    if (!hasAdminRole) {
      redirect("/home");
    }
  } catch (error) {
    if (error instanceof SessionRequiredError) {
      redirect("/sign-in?next=/admin");
    }
    throw error;
  }
}

export default async function AdminLayout({
  children,
}: Readonly<{ children: ReactNode }>) {
  await requireAdminAccess();
  return <AdminWorkspace>{children}</AdminWorkspace>;
}
