import { Link, useRouterState } from "@tanstack/react-router";
import { Home, ClipboardList, User } from "lucide-react";
export function BottomNav() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const items = [
    { to: "/profile", label: "Profile", icon: User },
    { to: "/home", label: "Home", icon: Home },
    { to: "/orders", label: "Orders", icon: ClipboardList },
  ] as const;
  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-cream/95 backdrop-blur md:hidden">
      <ul className="mx-auto flex max-w-lg items-center justify-around px-2 py-2">
        {items.map(({ to, label, icon: Icon }) => {
          const active = pathname === to;
          return (
            <li key={to} className="flex-1">
              <Link
                to={to}
                className={`flex flex-col items-center gap-0.5 rounded-lg py-1.5 text-[11px] font-semibold transition-colors ${
                  active ? "text-primary" : "text-ink/70 hover:text-primary"
                }`}
              >
                <Icon className={`h-5 w-5 ${active ? "fill-primary/10" : ""}`} />
                {label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
/** Desktop-friendlier variant shown as a fixed island on wide screens too */
export function BottomNavAll() {
  const pathname = useRouterState({ select: (s) => s.location.pathname });
  const items = [
    { to: "/profile", label: "Profile", icon: User },
    { to: "/home", label: "Home", icon: Home },
    { to: "/orders", label: "Orders", icon: ClipboardList },
  ] as const;
  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 border-t border-border bg-cream/95 backdrop-blur">
      <ul className="mx-auto flex max-w-lg items-center justify-around px-2 py-2">
        {items.map(({ to, label, icon: Icon }) => {
          const active = pathname === to;
          return (
            <li key={to} className="flex-1">
              <Link
                to={to}
                className={`flex flex-col items-center gap-0.5 rounded-lg py-1.5 text-[11px] font-semibold transition-colors ${
                  active ? "text-primary" : "text-ink/70 hover:text-primary"
                }`}
              >
                <Icon className="h-5 w-5" />
                {label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
