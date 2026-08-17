"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  BadgeIndianRupee,
  CalendarDays,
  ChefHat,
  ClipboardCheck,
  ClipboardList,
  Gauge,
  Home,
  ShieldCheck,
  Store,
  Utensils,
} from "lucide-react";

const links = [
  { href: "/chef", label: "Overview", icon: Home },
  { href: "/chef/application", label: "Application", icon: ClipboardCheck },
  { href: "/chef/kitchen", label: "Kitchen", icon: Store },
  { href: "/chef/menu", label: "Menu", icon: Utensils },
  { href: "/chef/meal-plans", label: "Meal Plans", icon: CalendarDays },
  { href: "/chef/capacity", label: "Capacity", icon: Gauge },
  { href: "/chef/orders", label: "Orders", icon: ClipboardList },
  { href: "/chef/earnings", label: "Earnings", icon: BadgeIndianRupee },
  { href: "/chef/operations", label: "Operations", icon: ShieldCheck },
] as const;

export function ChefWorkspaceNavigation() {
  const pathname = usePathname();

  return (
    <nav className="chef-panel-navigation" aria-label="Chef workspace navigation">
      {links.map((link) => {
        const active =
          pathname === link.href ||
          (link.href !== "/chef" && pathname.startsWith(`${link.href}/`));
        return (
          <Link
            key={link.href}
            href={link.href}
            aria-current={active ? "page" : undefined}
            className={`chef-panel-nav-link inline-flex items-center gap-2 ${
              active ? "!bg-primary !text-white" : ""
            }`}
          >
            <link.icon className="h-4 w-4" aria-hidden="true" />
            {link.label}
          </Link>
        );
      })}
      <span className="sr-only">
        <ChefHat />
      </span>
    </nav>
  );
}

export default ChefWorkspaceNavigation;
