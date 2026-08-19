import { Activity, ArrowRight, CheckCircle2, Cloud, Database, Globe, Lock, MessageSquare, MonitorSmartphone, ServerCog, ShieldAlert } from "lucide-react";
import type { LucideIcon } from "lucide-react";

type WorkstreamKey = "backend" | "web" | "mobile" | "database" | "integrations" | "cloud";

type StepStatus = "locked" | "next" | "watch";

type WorkstreamCard = {
  key: WorkstreamKey;
  title: string;
  summary: string;
  icon: LucideIcon;
  readiness: string;
  actions: string[];
};

type SequenceStep = {
  order: number;
  title: string;
  detail: string;
  status: StepStatus;
};

type RiskCard = {
  title: string;
  impact: string;
  mitigation: string;
  icon: LucideIcon;
};

const workstreams: WorkstreamCard[] = [
  {
    key: "backend",
    title: "Spring Boot service completion",
    summary: "Standardize auth, RBAC, idempotency, outbox behavior, and provider orchestration exclusively in Java services.",
    icon: ServerCog,
    readiness: "Highest dependency for every downstream web and admin flow.",
    actions: [
      "Harden auth-service refresh, logout, and Redis-backed revocation semantics.",
      "Consolidate payment and delivery ownership inside integration-service.",
      "Close service-by-service production gaps before widening E2E scope."
    ]
  },
  {
    key: "web",
    title: "Canonical Next.js platform",
    summary: "Keep apps/customer-web-next as the single production web surface for customer, chef, and protected admin journeys.",
    icon: Globe,
    readiness: "This branch now exposes a dedicated build-train control page for sequencing and review.",
    actions: [
      "Validate BFF routes against Spring contracts route-by-route.",
      "Keep admin workflows inside protected Next.js routes, not legacy portals.",
      "Retire assumptions that depend on deprecated apps/api or customer-web."
    ]
  },
  {
    key: "mobile",
    title: "Launch-scope mobile decision",
    summary: "Proceed with React Native only after backend and web contracts stabilize and launch scope is confirmed.",
    icon: MonitorSmartphone,
    readiness: "Responsive web can launch first without blocking production readiness.",
    actions: [
      "Confirm whether native mobile is in launch scope.",
      "Avoid reviving Flutter or parallel backend assumptions.",
      "Sequence mobile after web and service contract stabilization."
    ]
  },
  {
    key: "database",
    title: "PostgreSQL + PostGIS validation",
    summary: "Validate Flyway bootstrap order, PostGIS prerequisites, outbox tables, and retention-safe schema ownership.",
    icon: Database,
    readiness: "Migration reliability is a release gate, not a follow-up task.",
    actions: [
      "Run empty-database bootstrap checks for every Spring service.",
      "Verify PostGIS installation and nearby-discovery performance paths.",
      "Document rollback and restore runbooks for forward-only migrations."
    ]
  },
  {
    key: "integrations",
    title: "Provider normalization",
    summary: "Retain Razorpay and delivery providers behind normalized integration-service adapters with webhook replay safety.",
    icon: MessageSquare,
    readiness: "Checkout, refunds, and delivery tracking depend on normalized provider contracts.",
    actions: [
      "Enforce signature verification and dedupe keys on webhook ingress.",
      "Publish normalized payment and delivery events for downstream consumers.",
      "Verify notification delivery and recovery operational controls."
    ]
  },
  {
    key: "cloud",
    title: "Azure deployability alignment",
    summary: "Align Container Apps, APIM, Redis, PostgreSQL, and secret wiring to the real production module graph.",
    icon: Cloud,
    readiness: "Infra must match actual service ownership before release gates can be trusted.",
    actions: [
      "Replace placeholder image contracts with deployable service inputs.",
      "Harden secret references, probes, ingress, and monitoring baselines.",
      "Keep workflows release-gated and manual-dispatch focused."
    ]
  }
];

const sequence: SequenceStep[] = [
  {
    order: 1,
    title: "Architecture lock and repo guardrails",
    detail: "Freeze canonical modules, deprecate conflicting surfaces, and prevent production drift.",
    status: "locked"
  },
  {
    order: 2,
    title: "Infra deployability alignment",
    detail: "Make Azure Container Apps, APIM, Redis, PostgreSQL, and secret bindings reflect real deployable services.",
    status: "locked"
  },
  {
    order: 3,
    title: "Auth, session, and RBAC hardening",
    detail: "Close revocation, token exchange, and privileged fail-closed paths before broadening admin scope.",
    status: "next"
  },
  {
    order: 4,
    title: "Admin closure in customer-web-next",
    detail: "Finish protected admin workflows in the canonical Next.js app and remove dependence on legacy portals.",
    status: "next"
  },
  {
    order: 5,
    title: "Payment and delivery standardization",
    detail: "Normalize provider orchestration in integration-service and validate downstream event ownership.",
    status: "next"
  },
  {
    order: 6,
    title: "BFF, data, and release gates",
    detail: "Validate contracts, E2E journeys, observability, rollback drills, and manual approval gates.",
    status: "watch"
  }
];

const risks: RiskCard[] = [
  {
    title: "Architecture drift",
    impact: "Teams may continue implementing features in deprecated surfaces such as apps/api or old web apps.",
    mitigation: "Keep apps/customer-web-next visibly canonical and front-load deprecation messaging.",
    icon: ShieldAlert
  },
  {
    title: "Auth control inconsistency",
    impact: "Privileged actions become unsafe if revocation, audience validation, or RBAC rules differ by surface.",
    mitigation: "Finish Redis-backed fail-closed controls before expanding admin operational reach.",
    icon: Lock
  },
  {
    title: "Infra and service mismatch",
    impact: "Completed UI and service flows can still fail if Azure image contracts and probes do not match reality.",
    mitigation: "Treat deployability alignment as an early train stop rather than a final packaging task.",
    icon: Activity
  }
];

const statusStyles: Record<StepStatus, string> = {
  locked: "border-emerald-400/30 bg-emerald-500/10 text-emerald-200",
  next: "border-amber-400/30 bg-amber-500/10 text-amber-100",
  watch: "border-slate-400/20 bg-slate-500/10 text-slate-200"
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
                This workspace turns the approved program plan into a web-facing execution board for <span className="font-semibold text-white">apps/customer-web-next</span>. It keeps the train aligned to Spring Boot backends, a single Next.js surface, PostgreSQL + PostGIS, Redis security state, Azure runtime controls, and provider logic centralized behind integration-service.
              </p>
            </div>
            <div className="grid gap-3 sm:grid-cols-3">
              <TrainMetric label="Canonical web module" value="apps/customer-web-next" />
              <TrainMetric label="Target backend runtime" value="Java 21 + Spring Boot 3" />
              <TrainMetric label="Primary release constraint" value="No Node.js production backend" />
            </div>
          </div>
          <div className="rounded-[28px] border border-white/10 bg-white/5 p-5 backdrop-blur">
            <p className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-300">Immediate web outcomes</p>
            <ul className="mt-4 space-y-3 text-sm text-slate-100">
              {[
                "Expose a dedicated build-train route inside the canonical Next.js app.",
                "Make the approved sequencing visible for customer, chef, and admin stakeholders.",
                "Keep production ownership centered on the Spring service graph and protected BFF routes."
              ].map(item => (
                <li key={item} className="flex gap-3 rounded-2xl border border-white/10 bg-slate-950/30 px-4 py-3">
                  <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-emerald-300" />
                  <span>{item}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </section>

      <section className="grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
        {workstreams.map(({ key, title, summary, icon: Icon, readiness, actions }) => (
          <article key={key} className="rounded-[28px] border border-slate-800 bg-slate-900/80 p-6 shadow-[0_20px_60px_-40px_rgba(15,23,42,0.85)]">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.26em] text-slate-400">{key}</p>
                <h2 className="mt-2 text-xl font-bold text-white">{title}</h2>
              </div>
              <div className="rounded-2xl border border-violet-400/20 bg-violet-500/10 p-3 text-violet-200">
                <Icon className="size-5" />
              </div>
            </div>
            <p className="mt-4 text-sm leading-7 text-slate-300">{summary}</p>
            <div className="mt-4 rounded-2xl border border-slate-800 bg-slate-950/70 p-4 text-sm text-slate-200">
              <p className="font-semibold text-white">Readiness note</p>
              <p className="mt-2 leading-6">{readiness}</p>
            </div>
            <ul className="mt-4 space-y-2 text-sm text-slate-200">
              {actions.map(action => (
                <li key={action} className="flex gap-2">
                  <ArrowRight className="mt-1 size-4 shrink-0 text-orange-300" />
                  <span>{action}</span>
                </li>
              ))}
            </ul>
          </article>
        ))}
      </section>

      <section className="grid gap-6 lg:grid-cols-[1.15fr_0.85fr]">
        <article className="rounded-[28px] border border-slate-800 bg-slate-900/80 p-6">
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">Execution order</p>
              <h2 className="mt-2 text-2xl font-bold text-white">Recommended train sequence</h2>
            </div>
            <div className="rounded-full border border-emerald-400/30 bg-emerald-500/10 px-3 py-1 text-xs font-semibold uppercase tracking-[0.22em] text-emerald-100">
              Web aligned
            </div>
          </div>
          <div className="mt-6 space-y-4">
            {sequence.map(step => (
              <div key={step.order} className={`rounded-[24px] border p-5 ${statusStyles[step.status]}`}>
                <div className="flex items-center justify-between gap-4">
                  <div className="flex items-center gap-3">
                    <span className="grid size-10 place-items-center rounded-2xl bg-white/10 text-sm font-black text-white">
                      {step.order}
                    </span>
                    <div>
                      <h3 className="text-base font-bold text-white">{step.title}</h3>
                      <p className="mt-1 text-sm leading-6 text-current/90">{step.detail}</p>
                    </div>
                  </div>
                  <span className="rounded-full border border-white/10 px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.22em] text-white/90">
                    {step.status}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </article>

        <article className="rounded-[28px] border border-slate-800 bg-slate-900/80 p-6">
          <p className="text-xs font-semibold uppercase tracking-[0.24em] text-slate-400">Manual decisions</p>
          <h2 className="mt-2 text-2xl font-bold text-white">What still needs human confirmation</h2>
          <ul className="mt-6 space-y-4 text-sm text-slate-200">
            {[
              "Confirm apps/customer-web-next as the only production customer and admin web entry point.",
              "Confirm mobile launch scope before any React Native bootstrap work begins.",
              "Approve Razorpay and launch delivery providers as the authoritative production set.",
              "Provision Azure non-prod resources and Key Vault wiring before release drills.",
              "Schedule security review, rollback rehearsal, and launch-governance sign-off."
            ].map(item => (
              <li key={item} className="rounded-2xl border border-slate-800 bg-slate-950/70 px-4 py-3 leading-6">
                {item}
              </li>
            ))}
          </ul>
        </article>
      </section>

      <section className="grid gap-4 lg:grid-cols-3">
        {risks.map(({ title, impact, mitigation, icon: Icon }) => (
          <article key={title} className="rounded-[24px] border border-slate-800 bg-slate-900/80 p-5">
            <div className="flex items-center gap-3 text-orange-200">
              <div className="rounded-2xl border border-orange-400/20 bg-orange-500/10 p-2.5">
                <Icon className="size-5" />
              </div>
              <h3 className="text-lg font-bold text-white">{title}</h3>
            </div>
            <p className="mt-4 text-sm leading-6 text-slate-300"><span className="font-semibold text-white">Impact:</span> {impact}</p>
            <p className="mt-3 text-sm leading-6 text-slate-300"><span className="font-semibold text-white">Mitigation:</span> {mitigation}</p>
          </article>
        ))}
      </section>
    </div>
  );
}

function TrainMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-[22px] border border-white/10 bg-white/5 p-4 backdrop-blur">
      <p className="text-[11px] font-semibold uppercase tracking-[0.22em] text-slate-300">{label}</p>
      <p className="mt-2 text-sm font-bold text-white">{value}</p>
    </div>
  );
}
