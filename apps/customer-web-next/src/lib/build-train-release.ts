export type BuildTrainFocusArea = {
  title: string;
  summary: string;
  bullets: string[];
};

export type BuildTrainGuardrail = {
  label: string;
  detail: string;
};

export type BuildTrainReleasePlan = {
  heading: string;
  subheading: string;
  architecture: string[];
  focusAreas: BuildTrainFocusArea[];
  guardrails: BuildTrainGuardrail[];
  manualActions: string[];
};

export const buildTrainReleasePlan: BuildTrainReleasePlan = {
  heading: "Production-readiness train for the canonical Craves web platform",
  subheading:
    "The supported launch surface is the Next.js customer, chef, admin, and BFF application in apps/customer-web-next with Razorpay retained as the primary payment rail and Azure as the runtime target.",
  architecture: [
    "Canonical web and admin surface: Next.js only in apps/customer-web-next.",
    "Backend system of record: Spring Boot services behind controlled BFF access.",
    "Primary database and geo stack: PostgreSQL with PostGIS.",
    "Revocation and cache expectations: Redis-backed server validation patterns.",
    "Cloud runtime target: Azure with managed identity-first integration.",
    "Primary payment rail: Razorpay remains active for checkout and verification.",
  ],
  focusAreas: [
    {
      title: "BFF-only browser integration",
      summary:
        "Client components must stay on same-origin Next.js routes so protected APIM and backend traffic never leaks into browser-visible configuration.",
      bullets: [
        "Mutating browser flows stay behind same-origin API routes.",
        "Protected service URLs remain server-only environment variables.",
        "Security headers and non-cacheable role workspaces are enforced at the web edge.",
      ],
    },
    {
      title: "Role-segregated workspaces",
      summary:
        "Admin, chef, and signed-in customer flows use explicit route authorization and non-public caching semantics.",
      bullets: [
        "Anonymous visitors are redirected away from private workspaces.",
        "Admin paths remain isolated from chef and customer surfaces.",
        "Chef operations keep no-store delivery and order controls.",
      ],
    },
    {
      title: "Manual release readiness checks",
      summary:
        "The build train page gives operators and reviewers a web-native snapshot of what still requires human coordination before production claims are made.",
      bullets: [
        "Webhook registration and payment sandbox validation are still human-owned prerequisites.",
        "Allowed image hosts and APIM hostnames must be confirmed outside the repository.",
        "Legacy non-Next web surfaces stay frozen and out of the default execution path.",
      ],
    },
  ],
  guardrails: [
    {
      label: "Canonical Next.js platform",
      detail:
        "Customer, chef, admin, and BFF capabilities are consolidated into this application; unsupported legacy web surfaces are not part of the launch path.",
    },
    {
      label: "Razorpay preservation",
      detail:
        "Checkout readiness keeps Razorpay active as the primary payment integration while browser traffic stays on same-origin handlers.",
    },
    {
      label: "Secure deployment posture",
      detail:
        "Remote image hosts are explicitly allowlisted and private routes emit no-store cache directives with secure response headers.",
    },
    {
      label: "Human approval gates",
      detail:
        "Environment hostnames, webhook registration, and secret population are manual actions and are intentionally not committed into the repository.",
    },
  ],
  manualActions: [
    "Confirm production hostnames for the Next.js web origin and protected APIM/base service routing.",
    "Populate server-only environment variables in Azure or the chosen secret store; never expose them as NEXT_PUBLIC values.",
    "Register Razorpay and delivery provider webhooks against the approved stable ingress paths.",
    "Validate allowed remote image hosts before broadening the Next.js image allowlist.",
    "Keep legacy apps/api, apps/customer-web, and apps/admin-portal out of default operational runbooks.",
  ],
};
