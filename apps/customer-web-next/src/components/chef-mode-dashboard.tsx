"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  BadgeIndianRupee,
  ChefHat,
  ChevronRight,
  ClipboardCheck,
  ClipboardList,
  ImageIcon,
  LogIn,
  MapPinned,
  ShieldCheck,
  Store,
  Utensils,
} from "lucide-react";
import {
  parseChefApplication,
  type ChefApplication,
} from "@/lib/chef-application-contract";
import { parseChefKitchen } from "@/lib/chef-kitchen-contract";
import type { ChefKitchen } from "@/lib/chef-kitchen-types";
import { parseChefMenuItems, type ChefMenuItem } from "@/lib/chef-menu-contract";
import {
  parseChefOrdersResponse,
  type ChefOrder,
} from "@/lib/chef-order-contract";
import {
  parseChefEarnings,
  type ChefEarning,
} from "@/lib/chef-earnings-contract";
import { loadSession, type CravesUser } from "@/services/auth/cravesAuth";

type DashboardState = "loading" | "signed-out" | "applicant" | "approved" | "error";

type Snapshot = {
  application: ChefApplication | null;
  kitchen: ChefKitchen | null;
  menu: ChefMenuItem[];
  orders: ChefOrder[];
  earnings: ChefEarning[];
  unavailable: string[];
};

const EMPTY: Snapshot = {
  application: null,
  kitchen: null,
  menu: [],
  orders: [],
  earnings: [],
  unavailable: [],
};

function hasChefRole(user: CravesUser): boolean {
  return user.roles.some((role) => role.toUpperCase() === "CHEF");
}

async function responseBody(response: Response): Promise<unknown> {
  return response.json().catch(() => null);
}

export function ChefModeDashboard() {
  const [state, setState] = useState<DashboardState>("loading");
  const [user, setUser] = useState<CravesUser | null>(null);
  const [snapshot, setSnapshot] = useState<Snapshot>(EMPTY);
  const [message, setMessage] = useState("Loading chef access…");

  useEffect(() => {
    let active = true;

    void (async () => {
      const current = await loadSession();
      if (!active) return;
      setUser(current);
      if (!current) {
        setState("signed-out");
        setMessage("Sign in to continue to chef onboarding or chef operations.");
        return;
      }

      if (!hasChefRole(current)) {
        try {
          const response = await fetch("/api/chef/application", {
            cache: "no-store",
            credentials: "same-origin",
          });
          const raw = await responseBody(response);
          const application = response.ok ? parseChefApplication(raw) : null;
          setSnapshot({ ...EMPTY, application });
          setState("applicant");
          setMessage(
            application
              ? `Application status: ${application.status.replaceAll("_", " ")}.`
              : "Start or continue the chef application for administrator review.",
          );
        } catch {
          setState("applicant");
          setMessage("Chef application status is temporarily unavailable.");
        }
        return;
      }

      const requests = await Promise.allSettled([
        fetch("/api/chef/application", {
          cache: "no-store",
          credentials: "same-origin",
        }),
        fetch("/api/chef/kitchen", {
          cache: "no-store",
          credentials: "same-origin",
        }),
        fetch("/api/chef/menu", {
          cache: "no-store",
          credentials: "same-origin",
        }),
        fetch("/api/chef/orders", {
          cache: "no-store",
          credentials: "same-origin",
        }),
        fetch("/api/chef/earnings", {
          cache: "no-store",
          credentials: "same-origin",
        }),
      ]);

      if (!active) return;
      const unavailable: string[] = [];
      let application: ChefApplication | null = null;
      let kitchen: ChefKitchen | null = null;
      let menu: ChefMenuItem[] = [];
      let orders: ChefOrder[] = [];
      let earnings: ChefEarning[] = [];

      for (let index = 0; index < requests.length; index += 1) {
        const result = requests[index];
        const label = ["application", "kitchen", "menu", "orders", "earnings"][index]!;
        if (result.status !== "fulfilled" || !result.value.ok) {
          unavailable.push(label);
          continue;
        }
        const raw = await responseBody(result.value);
        if (index === 0) application = parseChefApplication(raw);
        if (index === 1) kitchen = raw === null ? null : parseChefKitchen(raw);
        if (index === 2) menu = parseChefMenuItems(raw) ?? [];
        if (index === 3) orders = parseChefOrdersResponse(raw) ?? [];
        if (index === 4) earnings = parseChefEarnings(raw) ?? [];
      }

      setSnapshot({
        application,
        kitchen,
        menu,
        orders,
        earnings,
        unavailable,
      });
      setState("approved");
      setMessage(
        unavailable.length
          ? `Chef access is active. ${unavailable.join(", ")} status is temporarily unavailable.`
          : "All chef workspace services responded successfully.",
      );
    })().catch((error) => {
      if (!active) return;
      setState("error");
      setMessage(
        error instanceof Error
          ? error.message
          : "Chef workspace could not be loaded.",
      );
    });

    return () => {
      active = false;
    };
  }, []);

  const stats = useMemo(() => {
    const activeMenu = snapshot.menu.filter((item) => item.status === "ACTIVE");
    const availableMenu = activeMenu.filter((item) => item.available);
    const actionOrders = snapshot.orders.filter(
      (order) => order.status === "CHEF_ACCEPTANCE_PENDING",
    );
    const activeOrders = snapshot.orders.filter((order) =>
      [
        "CHEF_ACCEPTED",
        "PREPARING",
        "READY_FOR_PICKUP",
        "OUT_FOR_DELIVERY",
      ].includes(order.status),
    );
    const pendingLedger = snapshot.earnings.filter((entry) =>
      ["APPROVED", "SETTLEMENT_PENDING"].includes(entry.status),
    );
    return {
      activeMenu: activeMenu.length,
      availableMenu: availableMenu.length,
      images: activeMenu.filter((item) => item.images.length > 0).length,
      actionOrders: actionOrders.length,
      activeOrders: activeOrders.length,
      pendingLedger: pendingLedger.length,
    };
  }, [snapshot]);

  if (state === "loading") {
    return (
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" aria-hidden="true">
        {Array.from({ length: 6 }, (_, index) => (
          <div key={index} className="h-44 animate-pulse rounded-2xl bg-grey-200" />
        ))}
        <p className="sr-only" role="status">Loading chef workspace</p>
      </div>
    );
  }

  if (state === "signed-out") {
    return (
      <section className="rounded-2xl border border-border bg-white p-8 text-center shadow-[var(--shadow-card)] md:p-12">
        <LogIn className="mx-auto h-10 w-10 text-primary" aria-hidden="true" />
        <h2 className="mt-4 font-display text-2xl font-bold text-ink">
          Sign in through the customer experience
        </h2>
        <p className="mx-auto mt-2 max-w-xl text-sm leading-6 text-muted-foreground">
          {message} Craves uses one Firebase phone identity for customer and chef modes.
        </p>
        <Link href="/" className="btn-primary mt-6 inline-flex">
          Open secure sign in
        </Link>
      </section>
    );
  }

  if (state === "applicant") {
    const application = snapshot.application;
    return (
      <section className="rounded-2xl border border-border bg-white p-6 shadow-[var(--shadow-card)] md:p-8">
        <div className="flex items-start gap-3">
          <ClipboardCheck className="mt-1 h-7 w-7 shrink-0 text-primary" aria-hidden="true" />
          <div>
            <p className="craves-overline text-primary">Chef onboarding</p>
            <h2 className="mt-1 font-display text-2xl font-bold text-ink">
              {application
                ? `Application ${application.status.replaceAll("_", " ").toLocaleLowerCase("en-IN")}`
                : "Start your chef application"}
            </h2>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">
              {message} Kitchen, menu, order and finance tools unlock only after the backend grants the CHEF role.
            </p>
          </div>
        </div>
        {application?.rejectionReason && (
          <p className="mt-5 rounded-xl border border-error/20 bg-error/5 p-3 text-sm text-error">
            Administrator note: {application.rejectionReason}
          </p>
        )}
        <Link href="/chef/application" className="btn-primary mt-6 inline-flex">
          Open chef application
        </Link>
      </section>
    );
  }

  if (state === "error") {
    return (
      <section className="rounded-2xl border border-error/20 bg-white p-8 text-center shadow-[var(--shadow-card)]">
        <AlertTriangle className="mx-auto h-10 w-10 text-error" aria-hidden="true" />
        <h2 className="mt-4 font-display text-2xl font-bold text-ink">
          Chef workspace unavailable
        </h2>
        <p className="mt-2 text-sm text-muted-foreground">{message}</p>
      </section>
    );
  }

  const kitchenMapped =
    typeof snapshot.kitchen?.latitude === "number" &&
    typeof snapshot.kitchen.longitude === "number";
  const cards = [
    {
      href: "/chef/application",
      label: "Application",
      title: snapshot.application?.status ?? "Status unavailable",
      description: "Admin approval and supported proof evidence",
      icon: ClipboardCheck,
    },
    {
      href: "/chef/kitchen",
      label: "Kitchen",
      title: snapshot.kitchen?.status ?? "Not created",
      description: kitchenMapped
        ? "Kitchen location ready for nearby discovery"
        : "Kitchen location requires confirmation",
      icon: Store,
    },
    {
      href: "/chef/menu",
      label: "Menu",
      title: `${stats.availableMenu} available of ${stats.activeMenu} active`,
      description: `${stats.images} active items have public images`,
      icon: Utensils,
    },
    {
      href: "/chef/orders",
      label: "Orders",
      title: `${stats.actionOrders} require action`,
      description: `${stats.activeOrders} orders currently in progress`,
      icon: ClipboardList,
    },
    {
      href: "/chef/earnings",
      label: "Earnings",
      title: `${stats.pendingLedger} pending ledger entries`,
      description: "Finance-owned allocations and settlement status",
      icon: BadgeIndianRupee,
    },
    {
      href: "/chef/operations",
      label: "Operations",
      title: "Readiness and compliance",
      description: "Approval, mapped location, availability and supported proof status",
      icon: ShieldCheck,
    },
  ] as const;

  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] md:p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <ChefHat className="mt-1 h-7 w-7 shrink-0 text-primary" aria-hidden="true" />
            <div>
              <p className="craves-overline text-primary">Authenticated chef</p>
              <h2 className="mt-1 font-display text-2xl font-bold text-ink">
                Welcome, {user?.firstName || user?.username || "Chef"}
              </h2>
              <p role="status" className="mt-2 text-sm leading-6 text-muted-foreground">
                {message}
              </p>
            </div>
          </div>
          <Link
            href="/chef/operations"
            className="inline-flex min-h-11 items-center gap-2 rounded-lg border border-primary px-4 text-sm font-semibold text-contrast-red hover:bg-secondary"
          >
            <MapPinned className="h-4 w-4" aria-hidden="true" />
            Check readiness
          </Link>
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {cards.map((card) => (
          <Link
            key={card.href}
            href={card.href}
            className="group flex min-h-44 flex-col rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)] transition hover:-translate-y-1 hover:border-primary/40"
          >
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-secondary text-primary">
              <card.icon className="h-5 w-5" aria-hidden="true" />
            </span>
            <p className="craves-overline mt-4 text-primary">{card.label}</p>
            <h3 className="mt-1 font-display text-xl font-bold text-ink">
              {card.title}
            </h3>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">
              {card.description}
            </p>
            <span className="mt-auto inline-flex items-center gap-1 pt-4 text-sm font-semibold text-contrast-red">
              Open workspace
              <ChevronRight className="h-4 w-4 transition-transform group-hover:translate-x-1" aria-hidden="true" />
            </span>
          </Link>
        ))}
      </section>

      <section className="grid gap-4 sm:grid-cols-3">
        <article className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]">
          <ImageIcon className="h-5 w-5 text-primary" aria-hidden="true" />
          <p className="mt-3 text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">
            Public images
          </p>
          <p className="mt-1 font-display text-2xl font-bold text-ink">
            {stats.images}/{stats.activeMenu}
          </p>
        </article>
        <article className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]">
          <ClipboardList className="h-5 w-5 text-primary" aria-hidden="true" />
          <p className="mt-3 text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">
            Orders requiring action
          </p>
          <p className="mt-1 font-display text-2xl font-bold text-ink">
            {stats.actionOrders}
          </p>
        </article>
        <article className="rounded-2xl border border-border bg-white p-5 shadow-[var(--shadow-card)]">
          <ShieldCheck className="h-5 w-5 text-primary" aria-hidden="true" />
          <p className="mt-3 text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">
            Kitchen status
          </p>
          <p className="mt-1 font-display text-2xl font-bold text-ink">
            {snapshot.kitchen?.status ?? "Not created"}
          </p>
        </article>
      </section>
    </div>
  );
}

export default ChefModeDashboard;
