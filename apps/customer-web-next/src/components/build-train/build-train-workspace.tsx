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
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

type WorkstreamKey =
  | "backend"
  | "web"
  | "mobile"
  | "database"
  | "integrations"
  | "cloud";

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
    summary:
      "Standardize auth, RBAC, idempotency, outbox behavior, and provider orchestration exclusively in Java services.",
    icon: ServerCog,
    readiness:
      "Highest dependency for every downstream web, checkout, and admin flow.",
    actions: [
      "Harden auth-service refresh, logout, and Redis-backed revocation semantics.",
      "Consolidate payment and delivery ownership inside integration-service.",
      "Close service-by-service production gaps before widening E2E scope.",
    ],
  },
  {
    key: "web",
    title: "Canonical Next.js platform",
    summary:
      "Keep apps/customer-web-next as the single production web surface for customer, chef, and protected admin journeys.",
    icon: Globe,
    readiness:
      "This branch now exposes a dedicated build-train control page for sequencing, release review, and product alignment.",
    actions: [
      "Validate BFF routes against Spring contracts route-by-route.",
      "Keep admin workflows inside protected Next.js routes, not legacy portals.",
      "Retire assumptions that depend on deprecated apps/api or apps/customer-web.",
    ],
  },
  {
    key: "mobile",
    title: "Launch-scope mobile decision",
    summary:
      "Proceed with React Native only after backend and web contracts stabilize and launch scope is explicitly confirmed.",
    icon: MonitorSmartphone,
    readiness:
      "Responsive web can launch first without blocking production readiness when native scope is undecided.",
    actions: [
      "Confirm whether native mobile is in launch scope.",
      "Avoid reviving Flutter or parallel backend assumptions.",
      "Sequence mobile after web and service contract stabilization.",
    ],
  },
  {
    key: "database",
    title: "PostgreSQL + PostGIS validation",
    summary:
      "Validate Flyway bootstrap order, PostGIS prerequisites, outbox tables, and retention-safe schema ownership.",
    icon: Database,
    readiness:
      "Migration reliability is a release gate, not a follow-up task.",
    actions: [
      "Run empty-database bootstrap checks for every Spring service.",
      "Verify PostGIS installation and nearby-discovery performance paths.",
      "Document restore, replay, and rollback runbooks for forward-only migrations.",
    ],
  },
  {
    key: "integrations",
    title: "Provider normalization",
    summary:
      "Retain Razorpay and delivery providers behind normalized integration-service adapters with webhook replay safety.",
    icon: MessageSquare,
    readiness:
      "Checkout, refunds, and delivery tracking depend on normalized provider contracts.",
    actions: [
      "Enforce signature verification and dedupe keys on webhook ingress.",
      "Publish normalized payment and delivery events for downstream consumers.",
      "Verify notification delivery and recovery operational controls.",
    ],
  },
  {
    key: "cloud",
    title: "Azure deployability alignment",
    summary:
      "Align Container Apps, APIM, Redis, PostgreSQL, and secret wiring to the real production module graph.",
    icon: Cloud,
    readiness:
      "Infra must match actual service ownership before release gates can be trusted.",
    actions: [
      "Replace placeholder image contracts with deployable service inputs.",
      "Harden secret references, probes, ingress, and monitoring baselines.",
      "Keep workflows release-gated and workflow_dispatch focused.",
    ],
  },
];

const sequence: SequenceStep[] = [
  {
    order: 1,
    title: "Architecture lock and repo guardrails",
    detail:
      "Freeze canonical modules, deprecate conflicting surfaces, and prevent production drift before feature hardening continues.",
    status: "locked",
  },
  {
    order: 2,
    title: "Infra deployability alignment",
    detail:
      "Make Azure Container Apps, APIM, Redis, PostgreSQL, and secret bindings reflect real deployable services and probes.",
    status: "locked",
  },
  {
    order: 3,
    title: "Auth, session, and RBAC hardening",
    detail:
      "Close revocation, token exchange, and privileged fail-closed paths before broadening admin scope.",
    status: "next",
  },
  {
    order: 4,
    title: "Admin closure in customer-web-next",
    detail:
      "Finish protected admin workflows in the canonical Next.js app and remove dependency on legacy portals.",
    status: "next",
  },
  {
    order: 5,
    title: "Payment and delivery standardization",
    detail:
      "Normalize provider orchestration in integration-service and validate downstream event ownership.",
    status: "next",
  },
  {
    order: 6,
    title: "BFF, data, and release gates",
    detail:
      "Validate contracts, E2E journeys, observability, rollback drills, and manual approval gates.",
    status: "watch",
  },
];

const risks: RiskCard[] = [
  {
    title: "Architecture drift",
    impact:
      "Teams may continue implementing features in deprecated surfaces such as apps/api or old web apps, creating launch ambiguity.",
    mitigation:
      "Keep apps/customer-web-next visibly canonical and front-load deprecation messaging plus manual release guardrails.",
    icon: ShieldAlert,
  },
  {
    title: "Auth control inconsistency",
    impact:
      "Privileged actions become unsafe if revocation, audience validation, or RBAC rules differ by surface.",
    mitigation:
      "Finish Redis-backed fail-closed controls before expanding admin operational reach.",
    icon: Lock,
  },
  {
    title: "Infra and service mismatch",
    impact:
      "Completed UI and service flows can still fail if Azure image contracts, probes, and secret bindings do not match reality.",
    mitigation:
      "Treat deployability alignment as an early train stop rather than a final packaging task.",
    icon: Activity,
  },
];

const statusStyles: Record<StepStatus, string> = {
  locked: "border-emerald-400/30 bg-emerald-500/10 text-emerald-200",
  next: "border-amber-400/30 bg-amber-500/10 text-amber-100",
  watch: "border-slate-400/20 bg-slate-500/10 text-slate-200",
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
                This workspace turns the approved program plan into a web-facing execution board for{