"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import {
  Bell,
  CalendarRange,
  ChefHat,
  ClipboardList,
  Home,
} from "lucide-react";
import {
  getSession,
  loadSession,
  subscribeSession,
} from "@/services/auth/cravesAuth";

const serviceLinks = [
  { href: "/home", label: "Discover", icon: Home },
  { href: "/orders", label: "Orders", icon: ClipboardList },
  { href: "/subscriptions", label: "Meal plans", icon: CalendarRange },
  { href: "/notifications", label: "Updates", icon: Bell },
  { href: "/chef", label: "Chef mode", icon: ChefHat },
] as const;

function isActiveRoute(pathname: string, href: string): boolean {
  return pathname === href || (href !== "/home" && pathname.startsWith(`${href}/`));
}

export function PersistentCustomerServiceNav({
  className = "",
}: {
  className?: string;
}) {
  const pathname = usePathname();
  const [signedIn, setSignedIn] = useState(Boolean(getSession()));

  useEffect(() => {
    let active = true;
    const updateFromMemory = () => {
      if (active) setSignedIn(Boolean(getSession()));
    };
    const unsubscribe = subscribeSession(updateFromMemory);

    if (!getSession()) {
      void loadSession()
        .then((user) => {
          if (active) setSignedIn(Boolean(user));
        })
        .catch(() => {
          if (active) setSignedIn(false);
        });
    }

    return () => {
      active = false;
      unsubscribe();
    };
  }, [pathname]);

  if (!signedIn) return null;

  return (
    <nav
      className={`flex gap-2 overflow-x-auto pb-1 ${className}`.trim()}
      aria-label="Customer services"
      data-customer-service-navigation="embedded"
    >
      {serviceLinks.map((link) => {
        const active = isActiveRoute(pathname, link.href);
        return (
          <Link
            key={link.href}
            href={link.href}
            aria-current={active ? "page" : undefined}
            className="inline-flex min-h-11 shrink-0 items-center gap-2 rounded-lg border border-[#F62E18] bg-white px-3 text-sm font-semibold text-black transition-colors hover:bg-[#F62E18] hover:font-bold hover:text-white focus-visible:bg-[#F62E18] focus-visible:font-bold focus-visible:text-white"
          >
            <link.icon className="h-4 w-4" aria-hidden="true" />
            {link.label}
          </Link>
        );
      })}
    </nav>
  );
}

export default PersistentCustomerServiceNav;
