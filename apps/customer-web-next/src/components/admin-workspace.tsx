"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { type ReactNode, useEffect, useState } from "react";
import {
  BellRing, ChefHat, CircleUserRound, ClipboardList, Gauge, LayoutDashboard,
  Menu, ReceiptText, SearchCheck, ShieldCheck, X
} from "lucide-react";
import type { AdminIdentity } from "@/lib/admin-contract";
import { SyncfusionLicense } from "@/components/syncfusion-license";

const navigation = [
  { href: "/admin", label: "Overview", icon: LayoutDashboard },
  { href: "/admin/chef-reviews", label: "Chef reviews", icon: ChefHat },
  { href: "/admin/subscription-plans", label: "Plans", icon: ReceiptText },
  { href: "/admin/subscriptions", label: "Subscriptions", icon: ClipboardList },
  { href: "/admin/subscription-capacity", label: "Capacity", icon: Gauge },
  { href: "/admin/operations", label: "Investigations", icon: SearchCheck },
  { href: "/admin/accounts", label: "Account security", icon: ShieldCheck },
  { href: "/admin/notifications", label: "Notification recovery", icon: BellRing }
];

export function AdminWorkspace({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const [identity, setIdentity] = useState<AdminIdentity | null>(null);
  const [message, setMessage] = useState("Verifying administrator access…");
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    let active = true;
    fetch("/api/admin/me", { cache: "no-store" })
      .then(async response => ({ response, body: await response.json().catch(() => null) }))
      .then(({ response, body }) => {
        if (!active) return;
        if (response.status === 401) throw new Error("Sign in with an administrator account.");
        if (response.status === 403) throw new Error("This account does not have administrator access.");
        if (!response.ok) throw new Error("Administrator identity is temporarily unavailable.");
        setIdentity(body as AdminIdentity);
        setMessage("");
      })
      .catch(error => active && setMessage(error instanceof Error ? error.message : "Administrator access is unavailable."));
    return () => { active = false; };
  }, []);

  if (!identity) {
    return <main className="flex min-h-screen items-center justify-center bg-[#f7f5fb] px-5">
      <section className="w-full max-w-lg rounded-[32px] border border-[#e9e4f2] bg-white p-8 text-center shadow-[0_24px_70px_-42px_rgba(56,39,83,0.45)]">
        <div className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-[#ede6ff] text-[#6930ca]"><ShieldCheck /></div>
        <h1 className="mt-6 text-2xl font-bold text-[#251b35]">Craves administration</h1>
        <p className="mt-3 text-sm text-[#71677d]" role="status">{message}</p>
        <Link href={`/sign-in?returnTo=${encodeURIComponent(pathname)}`} className="mt-6 inline-flex rounded-xl bg-[#6930ca] px-5 py-3 text-sm font-bold text-white">Administrator sign in</Link>
      </section>
    </main>;
  }

  return <div className="min-h-screen bg-[#f7f5fb] text-[#251b35]">
    <SyncfusionLicense />
    {menuOpen && <button aria-label="Close navigation overlay" className="fixed inset-0 z-40 bg-[#1b1229]/40 lg:hidden" onClick={() => setMenuOpen(false)} />}
    <aside className={`fixed inset-y-0 left-0 z-50 flex w-[280px] flex-col bg-[#241631] text-white transition-transform lg:translate-x-0 ${menuOpen ? "translate-x-0" : "-translate-x-full"}`}>
      <div className="flex h-24 items-center justify-between border-b border-white/10 px-7">
        <Link href="/admin" className="flex items-center gap-3" onClick={() => setMenuOpen(false)}>
          <span className="grid h-11 w-11 place-items-center rounded-2xl bg-gradient-to-br from-[#f6b545] to-[#e76547] font-black text-[#241631]">C</span>
          <span><strong className="block text-xl">Craves</strong><small className="text-[10px] font-bold uppercase tracking-[0.2em] text-[#cbbdd8]">Admin control</small></span>
        </Link>
        <button className="rounded-xl p-2 hover:bg-white/10 lg:hidden" onClick={() => setMenuOpen(false)} aria-label="Close navigation"><X size={20} /></button>
      </div>
      <nav className="flex-1 space-y-1 overflow-y-auto p-4" aria-label="Administrator modules">
        {navigation.map(item => {
          const selected = item.href === "/admin" ? pathname === item.href : pathname.startsWith(item.href);
          const Icon = item.icon;
          return <Link key={item.href} href={item.href} onClick={() => setMenuOpen(false)} aria-current={selected ? "page" : undefined}
            className={`flex items-center gap-3 rounded-2xl px-4 py-3 text-sm font-semibold transition ${selected ? "bg-[#6930ca] text-white shadow-lg shadow-[#140b20]/30" : "text-[#d8cfdf] hover:bg-white/8 hover:text-white"}`}>
            <Icon size={19} strokeWidth={2.2} />{item.label}
          </Link>;
        })}
      </nav>
      <div className="m-4 rounded-2xl border border-white/10 bg-white/5 p-4">
        <div className="flex items-center gap-3"><CircleUserRound className="text-[#f6b545]" /><div className="min-w-0"><p className="truncate text-sm font-bold">{identity.displayName || "Administrator"}</p><p className="truncate text-xs text-[#bfb2cb]">{identity.email || "Role verified"}</p></div></div>
        <p className="mt-3 text-[11px] leading-5 text-[#a99bb7]">Every action is authorized again by its owning backend service.</p>
      </div>
    </aside>
    <div className="lg:pl-[280px]">
      <header className="sticky top-0 z-30 flex h-20 items-center justify-between border-b border-[#e8e1ee] bg-white/90 px-5 backdrop-blur-xl sm:px-8">
        <div className="flex items-center gap-3"><button className="rounded-xl border border-[#e9e2ef] p-2.5 lg:hidden" onClick={() => setMenuOpen(true)} aria-label="Open navigation"><Menu size={20} /></button><div><p className="text-[10px] font-bold uppercase tracking-[0.18em] text-[#8a7a96]">Operations workspace</p><p className="text-sm font-bold text-[#362745]">Live backend data</p></div></div>
        <span className="hidden items-center gap-2 rounded-full bg-[#eaf8f0] px-3 py-2 text-xs font-bold text-[#23724a] sm:flex"><span className="h-2 w-2 rounded-full bg-[#31a66b]" />Admin role verified</span>
      </header>
      <main className="mx-auto max-w-[1600px] p-5 sm:p-8">{children}</main>
    </div>
  </div>;
}
