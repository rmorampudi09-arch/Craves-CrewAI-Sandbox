const executionTracks = [
  {
    name: "Platform production path freeze",
    branch: "crewai/platform-production-path-freeze",
    outcomes: ["Canonical production web path is customer-web-next", "Legacy web surfaces are clearly frozen", "Release path ambiguity is removed"],
  },
  {
    name: "Infra deployment foundations",
    branch: "crewai/infra-deployment-foundations",
    outcomes: ["Image references are parameterized", "Key Vault and runtime config expectations are documented", "Deploy-safety validation becomes release-ready"],
  },
  {
    name: "Security runtime hardening",
    branch: "crewai/security-runtime-hardening",
    outcomes: ["Session, revocation and abuse controls stay fail-closed", "Persona access boundaries are explicit", "Provider webhook protections remain auditable"],
  },
  {
    name: "Web admin console hardening",
    branch: "crewai/web-admin-console-hardening",
    outcomes: ["Admin workflows stay inside the canonical Next.js app", "Admin APIs enforce typed contracts and route guards", "Operational workflows are launch-focused"],
  },
  {
    name: "Integrations Razorpay productionization",
    branch: "crewai/integrations-razorpay-productionization",
    outcomes: ["Razorpay remains the retained production payment path", "Checkout, verify and reconciliation contracts stay aligned", "Frontend flow does not couple to provider internals"],
  },
  {
    name: "E2E core path validation",
    branch: "crewai/e2e-core-path-validation",
    outcomes: ["Customer, chef and admin paths gain browser validation", "Negative authorization cases are covered", "Mobile-web launch assumptions are testable"],
  },
  {
    name: "Docs readiness cleanup",
    branch: "crewai/docs-readiness-cleanup",
    outcomes: ["Architecture and release docs match the active code path", "Legacy deployment references are reduced", "Launch scope remains mobile-web first"],
  },
] as const;

const focusAreas = [
  "Canonical Next.js platform",
  "Admin closure in customer-web-next",
  "Razorpay-retained checkout path",
  "Persona route isolation",
  "BFF validation and same-origin protections",
  "Release-safe build and readiness validation",
] as const;

export function BuildTrainWorkspace() {
  return (
    <main className="min-h-screen bg-slate-950 px-6 py-12 text-white md:px-10">
      <div className="mx-auto max-w-6xl space-y-10">
        <section className="rounded-[32px] border border-white/10 bg-white/5 p-8 shadow-2xl shadow-black/30">
          <p className="text-xs font-semibold uppercase tracking-[0.24em] text-amber-300">Full build train request</p>
          <h1 className="mt-4 text-4xl font-black tracking-tight md:text-5xl">
            Production-readiness train for the canonical Craves web platform
          </h1>
          <p className="mt-4 max-w-3xl text-base leading-7 text-slate-200 md:text-lg">
            Canonical Next.js platform hardening stays focused on <span className="font-semibold text-white">apps/customer-web-next</span>.
            Admin closure, customer checkout, chef operations, security headers, BFF validation and Razorpay-retained flows are all tracked here.
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            {focusAreas.map((area) => (
              <span
                key={area}
                className="rounded-full border border-amber-300/30 bg-amber-300/10 px-4 py-2 text-sm font-medium text-amber-100"
              >
                {area}
              </span>
            ))}
          </div>
        </section>

        <section className="grid gap-5 lg:grid-cols-2">
          {executionTracks.map((track) => (
            <article key={track.branch} className="rounded-[28px] border border-white/10 bg-slate-900/80 p-6">
              <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-400">Execution track</p>
              <h2 className="mt-3 text-2xl font-bold text-white">{track.name}</h2>
              <code className="mt-3 block rounded-2xl bg-black/30 px-4 py-3 text-sm text-emerald-300">{track.branch}</code>
              <ul className="mt-5 space-y-3 text-sm leading-6 text-slate-200">
                {track.outcomes.map((outcome) => (
                  <li key={outcome} className="flex gap-3">
                    <span className="mt-2 h-2.5 w-2.5 rounded-full bg-emerald-400" aria-hidden />
                    <span>{outcome}</span>
                  </li>
                ))}
              </ul>
            </article>
          ))}
        </section>

        <section className="rounded-[28px] border border-white/10 bg-gradient-to-br from-amber-400/10 to-emerald-400/10 p-8">
          <h2 className="text-2xl font-bold">Web implementation commitments</h2>
          <div className="mt-5 grid gap-4 md:grid-cols-3">
            <div className="rounded-2xl border border-white/10 bg-black/20 p-5">
              <h3 className="font-semibold text-amber-200">Admin workflows</h3>
              <p className="mt-2 text-sm leading-6 text-slate-200">
                Direct access to administrator surfaces remains guarded, typed and non-cacheable across dashboard, accounts, operations, notifications and subscription tools.
              </p>
            </div>
            <div className="rounded-2xl border border-white/10 bg-black/20 p-5">
              <h3 className="font-semibold text-amber-200">Checkout and payment</h3>
              <p className="mt-2 text-sm leading-6 text-slate-200">
                Same-origin BFF enforcement, secure session cookies and Razorpay-aligned payment contracts keep the customer payment path production-oriented.
              </p>
            </div>
            <div className="rounded-2xl border border-white/10 bg-black/20 p-5">
              <h3 className="font-semibold text-amber-200">Mobile-web first</h3>
              <p className="mt-2 text-sm leading-6 text-slate-200">
                Launch scope stays aligned to the responsive web path. Native mobile is not revived in this train without explicit product re-approval.
              </p>
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}
