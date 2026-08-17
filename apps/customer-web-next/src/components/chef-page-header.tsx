import type { ReactNode } from "react";

export function ChefPageHeader({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <header className="rounded-2xl border border-border bg-white p-6 text-black shadow-[var(--shadow-card)] md:p-8">
      <div className="flex flex-wrap items-end justify-between gap-5">
        <div className="max-w-3xl">
          <p className="craves-overline text-[#C92716]">{eyebrow}</p>
          <h1 className="mt-2 font-display text-3xl font-bold tracking-[-0.045em] text-black md:text-4xl">
            {title}
          </h1>
          <p className="mt-3 text-sm leading-6 text-muted-foreground md:text-base">
            {description}
          </p>
        </div>
        {action ? <div className="shrink-0">{action}</div> : null}
      </div>
    </header>
  );
}

export default ChefPageHeader;
