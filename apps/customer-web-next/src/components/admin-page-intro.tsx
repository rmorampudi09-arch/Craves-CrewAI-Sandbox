import type { ReactNode } from "react";

export function AdminPageIntro({ eyebrow, title, description, children }: {
  eyebrow: string;
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return <section className="rounded-[28px] border border-[#e9e2ef] bg-white px-6 py-7 shadow-[0_18px_50px_-42px_rgba(54,35,72,0.5)] sm:px-8">
    <div className="flex flex-col justify-between gap-5 xl:flex-row xl:items-end">
      <div>
        <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#6930ca]">{eyebrow}</p>
        <h1 className="mt-3 text-3xl font-bold text-[#2c1e39] sm:text-4xl">{title}</h1>
        <p className="mt-3 max-w-3xl text-sm leading-6 text-[#74667f]">{description}</p>
      </div>
      {children}
    </div>
  </section>;
}
