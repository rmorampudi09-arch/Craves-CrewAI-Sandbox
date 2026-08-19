import { ShieldCheck, Workflow, Wrench } from "lucide-react";

import { buildTrainReleasePlan } from "@/lib/build-train-release";

const iconClassName = "h-5 w-5 text-orange-600";

export function BuildTrainWorkspace() {
  return (
    <main className="min-h-screen bg-stone-950 px-6 py-12 text-stone-100">
      <div className="mx-auto flex max-w-6xl flex-col gap-8">
        <section className="overflow-hidden rounded-3xl border border-orange-500/20 bg-gradient-to-br from-stone-900 via-stone-950 to-orange-950/30 p-8 shadow-2xl shadow-orange-950/30">
          <div className="flex flex-col gap-5 lg:max-w-4xl">
            <span className="w-fit rounded-full border border-orange-400/30 bg-orange-500/10 px-4 py-1 text-xs font-semibold uppercase tracking-[0.24em] text-orange-200">
              Full build train request
            </span>
            <h1 className="text-4xl font-semibold tracking-tight text-white sm:text-5xl">
              {buildTrainReleasePlan.heading}
            </h1>
            <p className="max-w-3xl text-base leading-7 text-stone-300 sm:text-lg">
              {buildTrainReleasePlan.subheading}
            </p>
          </div>
        </section>

        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {buildTrainReleasePlan.architecture.map((item) => (
            <article
              key={item}
              className="rounded-2xl border border-stone-800 bg-stone-900/80 p-5 shadow-lg shadow-black/20"
            >
              <div className="mb-3 flex items-center gap-3">
                <ShieldCheck className={iconClassName} />
                <h2 className="text-sm font-semibold uppercase tracking-[0.18em] text-orange-200">
                  Approved architecture
                </h2>
              </div>
              <p className="text-sm leading-6 text-stone-300">{item}</p>
            </article>
          ))}
        </section>

        <section className="grid gap-6 xl:grid-cols-3">
          {buildTrainReleasePlan.focusAreas.map((focusArea) => (
            <article
              key={focusArea.title}
              className="rounded-3xl border border-stone-800 bg-stone-900/80 p-6 shadow-lg shadow-black/20"
            >
              <div className="mb-4 flex items-center gap-3">
                <Workflow className={iconClassName} />
                <h2 className="text-xl font-semibold text-white">{focusArea.title}</h2>
              </div>
              <p className="text-sm leading-6 text-stone-300">{focusArea.summary}</p>
              <ul className="mt-4 space-y-3 text-sm leading-6 text-stone-200">
                {focusArea.bullets.map((bullet) => (
                  <li key={bullet} className="flex gap-3">
                    <span className="mt-2 h-2 w-2 rounded-full bg-orange-400" />
                    <span>{bullet}</span>
                  </li>
                ))}
              </ul>
            </article>
          ))}
        </section>

        <section className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
          <article className="rounded-3xl border border-stone-800 bg-stone-900/80 p-6 shadow-lg shadow-black/20">
            <div className="mb-5 flex items-center gap-3">
              <ShieldCheck className={iconClassName} />
              <h2 className="text-2xl font-semibold text-white">Guardrails enforced in the web layer</h2>
            </div>
            <div className="grid gap-4 md:grid-cols-2">
              {buildTrainReleasePlan.guardrails.map((guardrail) => (
                <div key={guardrail.label} className="rounded-2xl border border-stone-800/80 bg-stone-950/60 p-4">
                  <h3 className="text-sm font-semibold uppercase tracking-[0.16em] text-orange-200">
                    {guardrail.label}
                  </h3>
                  <p className="mt-2 text-sm leading-6 text-stone-300">{guardrail.detail}</p>
                </div>
              ))}
            </div>
          </article>

          <aside className="rounded-3xl border border-stone-800 bg-stone-900/80 p-6 shadow-lg shadow-black/20">
            <div className="mb-5 flex items-center gap-3">
              <Wrench className={iconClassName} />
              <h2 className="text-2xl font-semibold text-white">Manual actions still required</h2>
            </div>
            <ol className="space-y-4 text-sm leading-6 text-stone-200">
              {buildTrainReleasePlan.manualActions.map((action, index) => (
                <li key={action} className="flex gap-4 rounded-2xl border border-stone-800/80 bg-stone-950/60 p-4">
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-orange-500/15 text-sm font-semibold text-orange-200">
                    {index + 1}
                  </span>
                  <span>{action}</span>
                </li>
              ))}
            </ol>
          </aside>
        </section>
      </div>
    </main>
  );
}
