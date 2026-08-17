import Link from "next/link";
import type { ReactNode } from "react";

export function PublicPolicyPage({
  eyebrow,
  title,
  intro,
  children,
  policyStatus = "approved",
}: {
  eyebrow: string;
  title: string;
  intro: string;
  children: ReactNode;
  policyStatus?: "approved" | "pending";
}) {
  return (
    <main
      data-craves-policy-status={policyStatus}
      className="min-h-screen bg-white text-[#2B1A12]"
    >
      <header className="border-b border-black/5 bg-white">
        <div className="mx-auto flex min-h-20 max-w-5xl items-center justify-between px-4 md:px-6">
          <Link href="/" className="font-display text-2xl font-black tracking-[-0.04em] text-[#F62E18]">
            CRAVES
          </Link>
          <Link
            href="/"
            className="rounded-full border border-black/10 px-4 py-2 text-sm font-semibold transition-colors hover:border-[#F62E18]"
          >
            Back to Craves
          </Link>
        </div>
      </header>

      <article className="mx-auto max-w-3xl px-4 py-12 md:px-6 md:py-16">
        <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#F62E18]">{eyebrow}</p>
        <h1 className="mt-3 font-display text-4xl font-black tracking-[-0.04em] md:text-5xl">{title}</h1>
        <p className="mt-5 max-w-2xl text-base leading-7 text-black/65">{intro}</p>
        <div className="mt-10 space-y-9 text-[15px] leading-7 text-black/75">{children}</div>
      </article>

      <footer className="border-t border-black/5 bg-[#111111] text-white">
        <div className="mx-auto flex max-w-5xl flex-col gap-2 px-4 py-8 text-sm text-white/70 md:px-6">
          <span>© {new Date().getFullYear()} Craves · Food from home · Hyderabad, India</span>
          <span>
            Support: <a className="font-semibold text-white" href="mailto:support@craves.in">support@craves.in</a>
          </span>
        </div>
      </footer>
    </main>
  );
}

export function PolicySection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section>
      <h2 className="font-display text-2xl font-bold tracking-[-0.02em] text-[#2B1A12]">{title}</h2>
      <div className="mt-3 space-y-3">{children}</div>
    </section>
  );
}

export function PolicyList({ children }: { children: ReactNode }) {
  return <ul className="list-disc space-y-2 pl-5 marker:text-[#F62E18]">{children}</ul>;
}
