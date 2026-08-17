import type { Metadata } from "next";
import type { ReactNode } from "react";
import "@syncfusion/ej2-tailwind3-theme/styles/base/base.css";
import "@syncfusion/ej2-tailwind3-theme/styles/grid/grid.css";
import "@syncfusion/ej2-tailwind3-theme/styles/pager/pager.css";
import "@syncfusion/ej2-tailwind3-theme/styles/popup/popup.css";
import "@syncfusion/ej2-tailwind3-theme/styles/spinner/spinner.css";
import "@syncfusion/ej2-tailwind3-theme/styles/tooltip/tooltip.css";
import { AdminWorkspace } from "@/components/admin-workspace";

export const metadata: Metadata = {
  title: "Craves administration",
  robots: { index: false, follow: false }
};

export default function AdminLayout({ children }: Readonly<{ children: ReactNode }>) {
  return <AdminWorkspace>{children}</AdminWorkspace>;
}
