import {
  Activity,
  ArrowRight,
  CheckCircle2,
  Cloud,
  Database,
  Globe,
  Lock,
  MessageSquare,
  MonitorSmartphone,
  ServerCog,
  ShieldAlert,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

import {
  buildTrainConfig,
  type RiskCard,
  type SequenceStep,
  type StepStatus,
  type WorkstreamConfig,
  type WorkstreamKey,
} from '@/lib/build-train-config';

type IconMap = Record<WorkstreamKey, LucideIcon>;

const workstreamIcons: IconMap = {
  backend: ServerCog,
  web: Globe,
  mobile: MonitorSmartphone,
  database: Database,
  integrations: MessageSquare,
  cloud: Cloud,
};

const statusStyles: Record<StepStatus, string> = {
  locked: 'border-emerald-400/30 bg-emerald-500/10 text-emerald-200',
  next: 'border-amber-400/30 bg-amber-500/10 text-amber-100',
  watch: 'border-slate-400/20 bg-slate-500/10 text-slate-200',
};

export function BuildTrainWorkspace() {
  return (
    <div className="mx-auto flex w-full max-w-7xl flex-col gap-10 px-6 py-10 lg:px-10 lg:py-14">
      <section className="overflow-hidden rounded-[32px] border border-white/10 bg-gradient-to-br from-[#1a1028] via-[#0f172a] to-[#08111f] shadow-[0_35px_120px_-55px_rgba(124,58,237,0.6)]">
        <div className="grid gap-8 px-6 py-8 lg:grid-cols-[1.25fr_0.75fr] lg:px-10 lg:py-10">
          <div className="space-y-6">
            <span className="inline-flex items-center gap-2 rounded-full border border-violet-400/30 bg-violet-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.28em] text-violet-100">
              Full build train request
            </span>
            <div className="space-y-3">
              <h1 className="text-3xl font-black tracking-tight text-white sm:text-4xl lg:text-5xl">
                Production-readiness train for the canonical Craves web platform
              </h1>
              <p className="max-w-3xl text-sm leading-7 text-slate-300 sm:text-base">
                This workspace turns the approved program plan into a web-facing execution board for
                branch <span className="font-semibold text-white">{buildTrainConfig.branchName}</span>.
                It keeps the production direction anchored to{' '}
                <span className="font-semibold text-white">{buildTrainConfig.canonicalWebModule}</span>,
                sequences cross-domain dependencies, and calls out the release blockers that must stay
                visible while backend, database, integrations, cloud, and web converge.
              </p>
            </div>
            <div className="flex flex-wrap gap-3">
              {buildTrainConfig.activeDomains.map((domain) => (
                <span
                  key={domain}
                  className="rounded-full border border-slate-700 bg-slate-900/70 px-3 py-1 text-xs font-medium uppercase tracking-[0.2em] text-slate-200"
                >
                  {domain}
                </span>
              ))}
            </div>
          </div>

          <div className="rounded-[28px] border border-white/10 bg-white/5 p-6 backdrop-blur">
            <p className="text-xs font-semibold uppercase tracking-[0.28em] text-slate-400">
              Canonical release posture
            </p>
            <div className="mt-4 space-y-4 text-sm text-slate-200">
              <div className="rounded-2xl border border-emerald-400/20 bg-emerald-500/10 p-4">
                <p className="font-semibold text-emerald-100">Keep this as the production web app</p>
                <p className="mt-1 text-slate-200">{buildTrainConfig.canonicalWebModule}</p>
              </div>
              <div className="rounded-2xl border border-rose-400/20 bg-rose-500/10 p-4">
                <p className="font-semibold text-rose-100">Explicit non-goal</p>
                <p className="mt-1 text-slate-200">{buildTrainConfig.forbiddenRuntimeDirection}</p>
              </div>
              <div className="rounded-2xl border border-sky-400/20 bg-sky-500/10 p-4">
                <p className="font-semibold text-sky-100">Admin direction</p>
                <p className="mt-1 text-slate-200">
                  Consolidate operational admin inside protected Next.js routes instead of reviving
                  standalone legacy portals.
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section className="grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
        {buildTrainConfig.workstreams.map((workstream) => (
          <WorkstreamPanel key={workstream.key} workstream={workstream} />
        ))}
      </section>

      <section className="grid gap-8 lg:grid-cols-[1.05fr_0.95fr]">
        <div className="rounded-[28px] border border-white/10 bg-slate-950/70 p-6 shadow-[0_20px_80px_-50px_rgba(148,163,184,0.6)]">
          <div className="flex items-center gap-3">
            <CheckCircle2 className="h-5 w-5 text-emerald-300" />
            <div>
              <h2 className="text-xl font-semibold text-white">Execution sequence</h2>
              <p className="text-sm text-slate-400">
                Sequence the release train by dependency, not by convenience.
              </p>
            </div>
          </div>
          <div className="mt-6 space-y-4">
            {buildTrainConfig.sequence.map((step) => (
              <SequencePanel key={step.order} step={step} />
            ))}
          </div>
        </div>

        <div className="space-y-8">
          <div className="rounded-[28px] border border-white/10 bg-slate-950/70 p-6 shadow-[0_20px_80px_-50px_rgba(59,130,246,0.5)]">
            <div className="flex items-center gap-3">
              <ShieldAlert className="h-5 w-5 text-rose-300" />
              <div>
                <h2 className="text-xl font-semibold text-white">Release risks that stay hot</h2>
                <p className="text-sm text-slate-400">
                  These are the risks that can still invalidate a successful-looking web build.
                </p>
              </div>
            </div>
            <div className="mt-6 space-y-4">
              {buildTrainConfig.risks.map((risk) => (
                <RiskPanel key={risk.title} risk={risk} />
              ))}
            </div>
          </div>

          <div className="rounded-[28px] border border-white/10 bg-gradient-to-br from-slate-900 via-slate-950 to-black p-6">
            <div className="flex items-center gap-3">
              <Lock className="h-5 w-5 text-violet-200" />
              <div>
                <h2 className="text-xl font-semibold text-white">Web release operator notes</h2>
                <p className="text-sm text-slate-400">
                  Use the canonical Next.js app to reflect shared branch progress without confusing the
                  production path.
                </p>
              </div>
            </div>
            <ul className="mt-6 space-y-3 text-sm leading-7 text-slate-300">
              <li>
                • Keep <span className="font-semibold text-white">apps/customer-web-next/src/app</span>{' '}
                as the only customer/admin browser entrypoint that looks launch-ready.
              </li>
              <li>
                • Prefer shared configuration, typed contracts, and route-safe helpers so BFF behavior
                stays aligned with Spring APIs.
              </li>
              <li>
                • Treat manual workflows, provider registrations, and environment ownership as explicit
                handoff dependencies rather than hidden assumptions.
              </li>
              <li>
                • Use this page as a train dashboard, not a substitute for backend completion or human
                release approval.
              </li>
            </ul>
          </div>
        </div>
      </section>
    </div>
  );
}

function WorkstreamPanel({ workstream }: { workstream: WorkstreamConfig }) {
  const Icon = workstreamIcons[workstream.key];

  return (
    <article className="group rounded-[28px] border border-white/10 bg-slate-950/70 p-6 transition-transform duration-200 hover:-translate-y-1 hover:border-violet-400/30 hover:bg-slate-950">
      <div className="flex items-start justify-between gap-4">
        <div className="rounded-2xl border border-violet-400/20 bg-violet-500/10 p-3 text-violet-100">
          <Icon className="h-5 w-5" />
        </div>
        <span className="rounded-full border border-slate-700 bg-slate-900 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.2em] text-slate-300">
          {workstream.key}
        </span>
      </div>
      <div className="mt-5 space-y-3">
        <h3 className="text-lg font-semibold text-white">{workstream.title}</h3>
        <p className="text-sm leading-7 text-slate-300">{workstream.summary}</p>
        <p className="rounded-2xl border border-slate-800 bg-slate-900/80 p-4 text-sm leading-7 text-slate-200">
          {workstream.readiness}
        </p>
      </div>
      <ul className="mt-5 space-y-3">
        {workstream.actions.map((action) => (
          <li key={action} className="flex items-start gap-3 text-sm leading-7 text-slate-300">
            <ArrowRight className="mt-1 h-4 w-4 shrink-0 text-violet-300" />
            <span>{action}</span>
          </li>
        ))}
      </ul>
    </article>
  );
}

function SequencePanel({ step }: { step: SequenceStep }) {
  return (
    <div className="flex gap-4 rounded-[24px] border border-white/10 bg-white/5 p-4">
      <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl border border-white/10 bg-slate-900 text-sm font-bold text-white">
        {step.order}
      </div>
      <div className="flex-1 space-y-2">
        <div className="flex flex-wrap items-center gap-3">
          <h3 className="text-base font-semibold text-white">{step.title}</h3>
          <span className={`rounded-full border px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.2em] ${statusStyles[step.status]}`}>
            {step.status}
          </span>
        </div>
        <p className="text-sm leading-7 text-slate-300">{step.detail}</p>
      </div>
    </div>
  );
}

function RiskPanel({ risk }: { risk: RiskCard }) {
  return (
    <article className="rounded-[24px] border border-rose-400/20 bg-rose-500/5 p-5">
      <h3 className="text-base font-semibold text-white">{risk.title}</h3>
      <p className="mt-3 text-sm leading-7 text-slate-300">
        <span className="font-semibold text-rose-100">Impact:</span> {risk.impact}
      </p>
      <p className="mt-3 text-sm leading-7 text-slate-300">
        <span className="font-semibold text-emerald-100">Mitigation:</span> {risk.mitigation}
      </p>
    </article>
  );
}
