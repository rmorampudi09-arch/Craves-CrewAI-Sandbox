export type WorkstreamKey =
  | 'backend'
  | 'web'
  | 'mobile'
  | 'database'
  | 'integrations'
  | 'cloud';

export type StepStatus = 'locked' | 'next' | 'watch';

export type WorkstreamConfig = {
  key: WorkstreamKey;
  title: string;
  summary: string;
  readiness: string;
  actions: string[];
};

export type SequenceStep = {
  order: number;
  title: string;
  detail: string;
  status: StepStatus;
};

export type RiskCard = {
  title: string;
  impact: string;
  mitigation: string;
};

export const buildTrainConfig = {
  activeDomains: ['backend', 'web', 'mobile', 'database', 'integrations', 'cloud'] as const,
  branchName: 'crewai/full-build-train-request',
  canonicalWebModule: 'apps/customer-web-next',
  forbiddenRuntimeDirection: 'No Node.js backend path',
  workstreams: [
    {
      key: 'backend',
      title: 'Spring Boot service completion',
      summary:
        'Standardize auth, RBAC, idempotency, outbox behavior, and provider orchestration exclusively in Java services.',
      readiness:
        'Highest dependency for every downstream web, checkout, and admin flow.',
      actions: [
        'Harden auth-service refresh, logout, and Redis-backed revocation semantics.',
        'Consolidate payment and delivery ownership inside integration-service.',
        'Close service-by-service production gaps before widening E2E scope.',
      ],
    },
    {
      key: 'web',
      title: 'Canonical Next.js platform',
      summary:
        'Keep apps/customer-web-next as the single production web surface for customer, chef, and protected admin journeys.',
      readiness:
        'This branch now exposes a dedicated build-train control page for sequencing, release review, and product alignment.',
      actions: [
        'Validate BFF routes against Spring contracts route-by-route.',
        'Keep admin workflows inside protected Next.js routes, not legacy portals.',
        'Retire assumptions that depend on deprecated apps/api or apps/customer-web.',
      ],
    },
    {
      key: 'mobile',
      title: 'Launch-scope mobile decision',
      summary:
        'Proceed with React Native only after backend and web contracts stabilize and launch scope is explicitly confirmed.',
      readiness:
        'Responsive web can launch first without blocking production readiness when native scope is undecided.',
      actions: [
        'Confirm whether native mobile is in launch scope.',
        'Avoid reviving Flutter or parallel backend assumptions.',
        'Sequence mobile after web and service contract stabilization.',
      ],
    },
    {
      key: 'database',
      title: 'PostgreSQL + PostGIS validation',
      summary:
        'Validate Flyway bootstrap order, PostGIS prerequisites, outbox tables, and retention-safe schema ownership.',
      readiness:
        'Migration reliability is a release gate, not a follow-up task.',
      actions: [
        'Run empty-database bootstrap checks for every Spring service.',
        'Verify PostGIS installation and nearby-discovery performance paths.',
        'Document restore, replay, and rollback runbooks for forward-only migrations.',
      ],
    },
    {
      key: 'integrations',
      title: 'Provider normalization',
      summary:
        'Retain Razorpay and delivery providers behind normalized integration-service adapters with webhook replay safety.',
      readiness:
        'Checkout, refunds, and delivery tracking depend on normalized provider contracts.',
      actions: [
        'Enforce signature verification and dedupe keys on webhook ingress.',
        'Publish normalized payment and delivery events for downstream consumers.',
        'Verify notification delivery and recovery operational controls.',
      ],
    },
    {
      key: 'cloud',
      title: 'Azure deployability alignment',
      summary:
        'Align Container Apps, APIM, Redis, PostgreSQL, and secret wiring to the real production module graph.',
      readiness:
        'Infra must match actual service ownership before release gates can be trusted.',
      actions: [
        'Replace placeholder image contracts with deployable service inputs.',
        'Harden secret references, probes, ingress, and monitoring baselines.',
        'Keep workflows release-gated and workflow_dispatch focused.',
      ],
    },
  ] satisfies WorkstreamConfig[],
  sequence: [
    {
      order: 1,
      title: 'Architecture lock and repo guardrails',
      detail:
        'Freeze canonical modules, deprecate conflicting surfaces, and prevent production drift before feature hardening continues.',
      status: 'locked',
    },
    {
      order: 2,
      title: 'Infra deployability alignment',
      detail:
        'Make Azure Container Apps, APIM, Redis, PostgreSQL, and secret bindings reflect real deployable services and probes.',
      status: 'locked',
    },
    {
      order: 3,
      title: 'Auth, session, and RBAC hardening',
      detail:
        'Close revocation, token exchange, and privileged fail-closed paths before broadening admin scope.',
      status: 'next',
    },
    {
      order: 4,
      title: 'Admin closure in customer-web-next',
      detail:
        'Finish protected admin workflows in the canonical Next.js app and remove dependency on legacy portals.',
      status: 'next',
    },
    {
      order: 5,
      title: 'Payment and delivery standardization',
      detail:
        'Normalize provider orchestration in integration-service and validate downstream event ownership.',
      status: 'next',
    },
    {
      order: 6,
      title: 'BFF, data, and release gates',
      detail:
        'Validate contracts, E2E journeys, observability, rollback drills, and manual approval gates.',
      status: 'watch',
    },
  ] satisfies SequenceStep[],
  risks: [
    {
      title: 'Architecture drift',
      impact:
        'Teams may continue implementing features in deprecated surfaces such as apps/api or old web apps, creating launch ambiguity.',
      mitigation:
        'Keep apps/customer-web-next visibly canonical and front-load deprecation messaging plus manual release guardrails.',
    },
    {
      title: 'Auth control inconsistency',
      impact:
        'Privileged actions become unsafe if revocation, audience validation, or RBAC rules differ by surface.',
      mitigation:
        'Finish Redis-backed fail-closed controls before expanding admin operational reach.',
    },
    {
      title: 'Infra and service mismatch',
      impact:
        'Completed UI and service flows can still fail if Azure image contracts, probes, and secret bindings do not match reality.',
      mitigation:
        'Treat deployability alignment as an early train stop rather than a final packaging task.',
    },
  ] satisfies RiskCard[],
} as const;

export type BuildTrainConfig = typeof buildTrainConfig;
