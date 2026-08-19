import { buildTrainConfig } from '@/lib/build-train-config';

const statusClasses: Record<(typeof buildTrainConfig.sequence)[number]['status'], string> = {
  locked: 'border-sky-300/30 bg-sky-300/10 text-sky-100',
  next: 'border-amber-300/30 bg-amber-300/10 text-amber-100',
  watch: 'border-emerald-300/30 bg-emerald-300/10 text-emerald-100',
};

const statusLabels: Record<(typeof buildTrainConfig.sequence)[number]['status'], string> = {
  locked: 'Locked in',
  next: 'Next up',
  watch: 'Watch closely',
};

export function BuildTrainWorkspace() {
  return (
    <main className="min-h-screen bg-slate-950 px-6 py-12 text-white md:px-10">
      <div className="mx-auto max-w-7xl space-y-10">
        <section className="overflow-hidden rounded-[32px] border border-white/10 bg-[radial-gradient(circle_at_top_left,_rgba(245,158,11,0.20),_transparent_35%),radial-gradient(circle_at_top_right,_rgba(16,185,129,0.18),_transparent_30%),rgba(15,23,42,0.92)] p-8 shadow-2xl shadow-black/30">
          <div className="flex flex-col gap-8 xl:flex-row xl:items-end xl:justify-between">
            <div className="max-w-4xl">
              <p className="text-xs font-semibold uppercase tracking-[0.24em] text-amber-300">
                Full build train request
              </p>
              <h1 className="mt-4 text-4xl font-black tracking-tight md:text-5xl">
                Production-readiness train for the canonical Craves web platform
              </h1>
              <p className="mt-4 text-base leading-7 text-slate-200 md:text-lg">
                Active domains keep the web track enabled, so this workspace stays focused on{' '}
                <span className="font-semibold text-white">{buildTrainConfig.canonicalWebModule}</span> while
                sequencing backend, data, integrations, and cloud dependencies around the approved production path.
              </p>
            </div>

            <div className="grid gap-3 rounded-[28px] border border-white/10 bg-black/20 p-5 text-sm text-slate-200 sm:grid-cols-3 xl:min-w-[420px]">
              <MetricCard label="Shared branch" value={buildTrainConfig.branchName} />
              <MetricCard label="Canonical web" value={buildTrainConfig.canonicalWebModule} />
              <MetricCard label="Guardrail" value={buildTrainConfig.forbiddenRuntimeDirection} />
            </div>
          </div>

          <div className="mt-6 flex flex-wrap gap-3">
            {buildTrainConfig.activeDomains.map((domain) => (
              <span
                key={domain}
                className="rounded-full border border-white/10 bg-white/10 px-4 py-2 text-sm font-medium capitalize text-slate-100"
              >
                {domain}
              </span>
            ))}
          </div>
        </section>

        <section className="grid gap-5 xl:grid-cols-[1.1fr_0.9fr]">
          <article className="rounded-[28px] border border-white/10 bg-slate-900/85 p-7">
            <div className="flex items-center justify-between gap-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Execution order</p>
                <h2 className="mt-2 text-2xl font-bold text-white">Build train sequencing</h2>
              </div>
              <span className="rounded-full border border-amber-300/25 bg-amber-300/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em] text-amber-100">
                Admin-first priority
              </span>
            </div>

            <ol className="mt-6 space-y-4">
              {buildTrainConfig.sequence.map((step) => (
                <li key={step.order} className="rounded-3xl border border-white/10 bg-black/20 p-5">
                  <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                    <div className="flex gap-4">
                      <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-white/10 text-base font-bold text-white">
                        {step.order}
                      </span>
                      <div>
                        <h3 className="text-lg font-semibold text-white">{step.title}</h3>
                        <p className="mt-2 text-sm leading-6 text-slate-300">{step.detail}</p>
                      </div>
                    </div>
                    <span
                      className={`inline-flex rounded-full border px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em] ${statusClasses[step.status]}`}
                    >
                      {statusLabels[step.status]}
                    </span>
                  </div>
                </li>
              ))}
            </ol>
          </article>

          <article className="rounded-[28px] border border-white/10 bg-slate-900/85 p-7">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Launch risks</p>
            <h2 className="mt-2 text-2xl font-bold text-white">Things that can still derail release</h2>
            <div className="mt-6 space-y-4">
              {buildTrainConfig.risks.map((risk) => (
                <div key={risk.title} className="rounded-3xl border border-rose-300/15 bg-rose-300/5 p-5">
                  <h3 className="text-lg font-semibold text-white">{risk.title}</h3>
                  <p className="mt-3 text-sm leading-6 text-slate-300">
                    <span className="font-semibold text-rose-100">Impact:</span> {risk.impact}
                  </p>
                  <p className="mt-3 text-sm leading-6 text-slate-300">
                    <span className="font-semibold text-emerald-100">Mitigation:</span> {risk.mitigation}
                  </p>
                </div>
              ))}
            </div>
          </article>
        </section>

        <section className="rounded-[28px] border border-white/10 bg-slate-900/85 p-7">
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Domain board</p>
          <h2 className="mt-2 text-2xl font-bold text-white">Cross-domain workstreams</h2>
          <div className="mt-6 grid gap-5 xl:grid-cols-2">
            {buildTrainConfig.workstreams.map((stream) => (
              <article key={stream.key} className="rounded-3xl border border-white/10 bg-black/20 p-6">
                <div className="flex items-center justify-between gap-3">
                  <h3 className="text-xl font-semibold capitalize text-white">{stream.title}</h3>
                  <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs font-semibold uppercase tracking-[0.18em] text-slate-200">
                    {stream.key}
                  </span>
                </div>
                <p className="mt-3 text-sm leading-6 text-slate-300">{stream.summary}</p>
                <p className="mt-4 rounded-2xl border border-emerald-300/20 bg-emerald-300/5 p-4 text-sm leading-6 text-emerald-100">
                  {stream.readiness}
                </p>
                <ul className="mt-4 space-y-3 text-sm leading-6 text-slate-200">
                  {stream.actions.map((action) => (
                    <li key={action} className="flex gap-3">
                      <span className="mt-2 h-2.5 w-2.5 shrink-0 rounded-full bg-amber-300" aria-hidden />
                      <span>{action}</span>
                    </li>
                  ))}
                </ul>
              </article>
            ))}
          </div>
        </section>
      </div>
    </main>
  );
}

type MetricCardProps = {
  label: string;
  value: string;
};

function MetricCard({ label, value }: MetricCardProps) {
  return (
    <div className="rounded-2xl border border-white/10 bg-white/5 p-4">
      <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">{label}</p>
      <p className="mt-2 text-sm font-semibold leading-6 text-white">{value}</p>
    </div>
  );
}
