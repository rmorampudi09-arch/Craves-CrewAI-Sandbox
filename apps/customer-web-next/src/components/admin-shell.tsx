"use client";

import { useEffect, useState } from "react";
import type { AdminIdentity } from "@/lib/admin-contract";

const links = [
  { href: "/admin/chef-reviews", label: "Chef applications", description: "Review pending chef applications and record audited approve/reject decisions." },
  { href: "/admin/subscription-plans", label: "Subscription plans", description: "Create and manage subscription plan status using backend-owned values." },
  { href: "/admin/subscriptions", label: "Subscription operations", description: "Look up a subscription and apply an allowed administrative status transition." },
  { href: "/admin/operations", label: "Operational investigations", description: "Inspect privacy-reduced order, payment, refund and delivery evidence with a mandatory audit reason." },
  { href: "/admin/accounts", label: "Account security", description: "Load an exact identity and perform audited suspension or reactivation with typed confirmation." },
  { href: "/admin/notifications", label: "Notification recovery", description: "Inspect failed delivery requests and perform a single audited requeue without directly calling a provider." }
];

export function AdminShell() {
  const [identity, setIdentity] = useState<AdminIdentity | null>(null);
  const [message, setMessage] = useState("Verifying administrator access…");

  useEffect(() => {
    let active = true;
    fetch("/api/admin/me", { cache: "no-store" })
      .then(async response => ({ response, body: await response.json().catch(() => null) }))
      .then(({ response, body }) => {
        if (!active) return;
        if (response.status === 401) throw new Error("Sign in with an administrator account.");
        if (response.status === 403) throw new Error("This account does not have administrator access.");
        if (!response.ok) throw new Error("Administrator identity is temporarily unavailable.");
        setIdentity(body as AdminIdentity); setMessage("");
      })
      .catch(error => active && setMessage(error instanceof Error ? error.message : "Administrator access is unavailable."));
    return () => { active = false; };
  }, []);

  if (!identity) return <section className="rounded-[30px] bg-[#FFF8EC] p-7 text-slate-950"><p role="status">{message}</p><a href="/sign-in?returnTo=/admin" className="mt-5 inline-block rounded-2xl bg-[#6930CA] px-5 py-3 font-bold text-white">Administrator sign in</a></section>;
  return <section>
    <div className="rounded-[30px] bg-[#FFF8EC] p-7 text-slate-950"><p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930CA]">ADMIN ENABLED</p><h2 className="mt-3 text-3xl font-bold">Welcome{identity.displayName ? `, ${identity.displayName}` : ""}</h2><p className="mt-3 text-sm text-slate-600">This shell does not grant roles. Every operation is re-authorized by its owning backend service.</p></div>
    <div className="mt-6 grid gap-5 md:grid-cols-2 xl:grid-cols-3">{links.map(link => <a key={link.href} href={link.href} className="rounded-[28px] border border-white/10 bg-white/5 p-6 text-white transition hover:bg-white/10"><strong className="text-xl">{link.label}</strong><p className="mt-3 text-sm leading-6 text-slate-300">{link.description}</p></a>)}</div>
  </section>;
}
