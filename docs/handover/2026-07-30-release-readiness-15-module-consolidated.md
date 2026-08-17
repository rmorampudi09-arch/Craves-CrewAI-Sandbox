# Craves release-readiness handover — 15-module batch

## 1. Document purpose
This handover records the complete code-only release-readiness batch created after the subscription/backoffice APIM stack. It is intended to be the authoritative checklist for the later pipeline and manual rollout session.

## 2. Delivery boundary
The batch contains release validation, evidence generation, and read-only Azure checks. It contains no functional deployment, database migration execution, APIM mutation, provider activation, Firebase change, Cashfree request, DNS change, or mobile-store action.

## 3. Baseline branch
The batch is stacked on `feature/subscription-backoffice-apim-runbook`, the head branch of PR #68. That branch already includes subscription ownership hardening, customer subscription web/mobile flows, administrator chef review, subscription administration, and guarded APIM assets.

## 4. Batch pull-request range
The release-readiness modules occupy PRs #69 through #83. Every PR is intentionally draft and must be merged in strict parent order after its own CI succeeds.

## 5. Locked technology stack
Java services are Spring Boot 3, Java 21, Maven, PostgreSQL/Flyway, and Azure Container Apps. Web is Next.js/TypeScript/Tailwind. Mobile is React Native/TypeScript. Authentication remains Firebase plus Craves JWT exchange.

## 6. No product-rule invention
This batch does not define pricing, commissions, delivery radius, refund eligibility, subscription benefits, FSSAI policy, KYC approval rules, settlement timing, promotional economics, or provider selection rules.

## 7. Manual execution principle
All pipelines use `trigger: none` and `pr: none`. They are registered and run manually later, one at a time, against the exact reviewed branch head.

## 8. Azure service connection
Azure-reading stages use the existing Azure DevOps variable `AZURE_SERVICE_CONNECTION`, expected to resolve to `Craves-Dev-Service-Connection`. The service connection must not be broadened beyond the permissions needed for the later task.

## 9. GitHub read credential
The stacked-PR validator requires a secret Azure DevOps variable named `GITHUB_TOKEN_READONLY`. It needs repository metadata read permission only. The value must never be pasted into chat, source, logs, or non-secret variables.

## 10. Current runtime safety
Delivery command, create reconciliation, webhook processing, tracking reconciliation, status publisher, and Borzo remain disabled. Delivery intelligence remains enabled. No pipeline in this batch changes those values.

## 11. Module 1 — stacked PR validator
PR #69 adds `scripts/release/validate-stacked-pr-chain.sh`, its Azure pipeline, and handover. It validates a selected numeric PR range through GitHub read APIs.

## 12. Module 1 checks
Each discovered PR must remain open and draft. Explicitly unmergeable PRs fail the gate. Every PR base branch must equal the immediately preceding PR head branch.

## 13. Module 1 inputs
The default standalone pipeline range is configurable. The final orchestrator uses PR #68 through PR #82 so the complete integrated release-readiness chain is checked before PR #83 itself is merged.

## 14. Module 1 failure handling
A missing, closed, non-draft, unmergeable, or incorrectly based PR blocks rollout. The correct response is to repair branch history or PR metadata, not skip the validator.

## 15. Module 1 security
The checkout does not persist credentials. The GitHub token is injected only as `GH_TOKEN` for the script process and is never printed.

## 16. Module 2 — pipeline YAML validator
PR #70 adds `scripts/release/validate-pipeline-yaml.py` and its pipeline. It parses Azure DevOps and GitHub Actions YAML using pinned PyYAML 6.0.2.

## 17. Module 2 checks
Tabs are rejected. Top-level YAML must be a mapping. Azure files must contain steps, jobs, or stages. Unexpected automatic triggers are rejected for the manual-rollout repository.

## 18. Module 2 failure handling
A syntax or structure error must be corrected in the owning pipeline before functional CI starts. Do not run a pipeline that has not passed repository-wide YAML parsing.

## 19. Module 2 dependency
Python 3.12 is selected explicitly. The pipeline installs only the pinned parser dependency and does not execute application code.

## 20. Module 2 mutation boundary
The validator reads repository files only. It does not create Azure DevOps pipeline definitions automatically; registration remains a manual Azure DevOps action.

## 21. Module 3 — secret-material gate
PR #71 adds `scripts/release/scan-secret-material.sh` and its pipeline. It scans tracked text files for likely private keys, cloud keys, bearer tokens, connection strings, and hard-coded secrets.

## 22. Module 3 exclusions
Generated and binary paths are skipped, including `node_modules`, Maven targets, build outputs, archives, images, PDFs, JARs, keystores, and PKCS#12 files.

## 23. Module 3 output safety
Potential values are not echoed. Findings are identified by file and redacted match context so logs do not become a second secret leak.

## 24. Module 3 incident response
A genuine finding requires source removal, history review, and rotation of the affected credential. The gate must not be silenced with broad exclusions.

## 25. Module 3 runtime boundary
The scanner does not read Azure Key Vault, pipeline secret values, Firebase files, signing files, or provider consoles.

## 26. Module 4 — Java 21/Maven gate
PR #72 adds `scripts/release/validate-java21-maven.sh`. It locates every service POM under `services/*/pom.xml` and requires explicit Java 21 targeting.

## 27. Module 4 build behavior
Each service runs `mvn -B -ntp -DskipTests=false verify`. Compilation, tests, packaging checks, and plugin validations therefore run before any image build.

## 28. Module 4 failure handling
One failing service blocks the repository gate. The owning service must be corrected on its feature branch; unrelated services must not be deployed around the failure.

## 29. Module 4 network behavior
Maven dependency resolution may contact configured artifact repositories. It does not contact Azure application resources or PostgreSQL unless a service test is incorrectly written to require them.

## 30. Module 4 deployment boundary
The gate does not run Docker, ACR login, image push, Container App update, Flyway against Azure, or APIM configuration.

## 31. Module 5 — Node lockfile gate
PR #73 adds `scripts/release/validate-node-lockfiles.sh`. It discovers application package files below `apps` and requires Node.js 24 or newer.

## 32. Module 5 lockfile rule
Every application must contain a reviewed `package-lock.json`. Missing lockfiles fail closed because reproducible dependency resolution is mandatory before public deployment or native build.

## 33. Module 5 command order
The script runs `npm ci --ignore-scripts --no-audit --fund=false`. If defined, `typecheck`, `test`, and `build` scripts then run in that module.

## 34. Module 5 expected initial failure
Earlier web/mobile modules explicitly recorded that lockfiles still need to be generated and reviewed. This gate may fail initially; that is expected and must be resolved before deployment.

## 35. Module 5 security
Lifecycle scripts are disabled during dependency installation. Package publication and registry writes are absent.

## 36. Module 6 — Docker hardening gate
PR #74 adds `scripts/release/validate-dockerfiles.sh`. It statically inspects every Dockerfile outside generated dependency/build directories.

## 37. Module 6 image rule
Mutable `latest` base-image tags are rejected. Production images must use reviewed, pinned tags or digests.

## 38. Module 6 runtime-user rule
Every Dockerfile must declare an explicit user that is not `root` or UID 0. The application must be able to run with that user before deployment.

## 39. Module 6 secret and download rules
Secret-like `ARG` or `ENV` names are rejected. Remote URL downloads through Docker `ADD` are rejected. Each Dockerfile must define `ENTRYPOINT` or `CMD`.

## 40. Module 6 execution boundary
The gate does not build, scan, sign, push, pull, or delete an image. ACR and Container Apps are untouched.

## 41. Module 7 — Flyway ordering gate
PR #75 adds `scripts/release/validate-flyway-migrations.py`. It locates Flyway SQL files in every service migration directory.

## 42. Module 7 naming rule
Migration files must follow deterministic lowercase `V<version>__<description>.sql` naming. Duplicate versions within one service are rejected.

## 43. Module 7 ordering rule
Versions are compared in sorted order and must be strictly increasing. The gate prevents accidental duplicate or ambiguous release history.

## 44. Module 7 destructive-change rule
`DROP TABLE`, `DROP SCHEMA`, `DROP COLUMN`, and `TRUNCATE TABLE` require the explicit marker `CRAVES-REVIEWED-DESTRUCTIVE-MIGRATION` in the file.

## 45. Module 7 database boundary
The gate never connects to PostgreSQL. It does not run `migrate`, `repair`, `clean`, undo, restore, or SQL execution.

## 46. Module 8 — APIM policy gate
PR #76 adds `scripts/release/validate-apim-assets.sh`. It parses all policy XML with `xmllint` and validates APIM Bash scripts with `bash -n`.

## 47. Module 8 inheritance rule
Policies must retain a `<base />` element. A policy that mixes `backend-id` and `base-url` is rejected because inherited backend selection cannot be safely overridden that way.

## 48. Module 8 CORS and secret rules
Wildcard origins are rejected. Likely embedded credentials and hard-coded bearer tokens are rejected.

## 49. Module 8 confirmation rule
APIM scripts that appear to default a write confirmation to true are rejected. Configuration and rollback confirmations must remain false until manually selected.

## 50. Module 8 Azure boundary
The gate is static. It never calls Azure API Management and does not create, update, delete, import, export, or test an operation.

## 51. Module 9 — Container Apps preflight
PR #77 adds `scripts/release/verify-containerapps-readonly.sh`. It reads selected existing Container Apps in the production-low resource group.

## 52. Module 9 readiness checks
Each app must have a current image and latest revision, report `Running`, and have a latest revision that is active, provisioned, and running at least one replica.

## 53. Module 9 health-state decision
The check intentionally does not require Azure `healthState`. Earlier controlled tests demonstrated that valid active revisions can return `None` for this field.

## 54. Module 9 output
The script prints app name, latest and latest-ready revision names, running state, replica count, and image reference. It does not print environment values or secrets.

## 55. Module 9 mutation boundary
Only `az containerapp show` and `az containerapp revision show` are used. No update, revision copy, activation, deactivation, restart, scale, ingress, or secret command exists.

## 56. Module 10 — fail-closed controls
PR #78 adds `scripts/release/verify-failclosed-controls.sh`. It validates Integration Service source defaults and deployed environment values.

## 57. Module 10 disabled controls
Delivery command, create reconciliation, webhook processing, tracking reconciliation, status publisher, and Borzo must equal `false`.

## 58. Module 10 enabled control
Delivery intelligence must equal `true`. This preserves internal provider-neutral decision support without executing provider or status-publishing work.

## 59. Module 10 failure response
Any unexpected true value blocks the release session. The state must be investigated before running a functional or deployment pipeline.

## 60. Module 10 mutation boundary
The gate does not use `az containerapp update`. It never changes an environment variable or creates a revision.

## 61. Module 11 — service health smoke gate
PR #79 adds `scripts/release/smoke-containerapp-health.sh`. It resolves ingress FQDNs through Azure read APIs.

## 62. Module 11 endpoint order
For each service it checks `/actuator/health/readiness`, then `/actuator/health`, then `/health`. The first recognized healthy HTTP 200 response completes that service.

## 63. Module 11 response handling
The response body is stored in a temporary file, parsed for a recognized healthy status, and deleted. Bodies are not printed to logs.

## 64. Module 11 network safety
Only HTTPS GET requests with bounded connection and total timeouts are issued. No authentication token or application payload is sent.

## 65. Module 11 failure handling
A service without ingress or a healthy supported endpoint fails the gate. The owning deployment/health configuration must be corrected before proceeding.

## 66. Module 12 — rollback readiness gate
PR #80 adds `scripts/release/validate-rollback-readiness.sh`. It inspects deployment-capable Azure pipeline YAML files.

## 67. Module 12 immutable-image rule
Deployment pipelines using `latest` are rejected. An immutable Build ID, commit SHA, or explicit image tag is required.

## 68. Module 12 previous-image rule
A Container App deployment pipeline must visibly capture the currently deployed image before replacement through `az containerapp show` or a recognized previous-image variable.

## 69. Module 12 rollback-coverage rule
Each deployment-capable pipeline must have a matching rollback pipeline or an explicit rollback stage. CI and status-only pipelines are excluded.

## 70. Module 12 failure handling
The gate may expose older deployment pipelines that predate the current rollback standard. Those pipelines must be amended before use.

## 71. Module 13 — observability baseline
PR #81 adds `scripts/release/verify-observability-baseline.sh`. It combines source checks and Azure read-only checks.

## 72. Module 13 source probes
Each Spring `application.yml` must visibly configure Actuator management and health/probe behavior. Missing probes block deployment readiness.

## 73. Module 13 managed-environment logging
The script resolves every selected app's managed environment and requires the application log destination to be Log Analytics or Azure Monitor.

## 74. Module 13 billing boundary
The gate does not create or resize Log Analytics, Application Insights, diagnostic settings, dashboards, alerts, action groups, or retention policies.

## 75. Module 13 future work
Detailed SLOs, error budgets, alert thresholds, distributed tracing, and long-term retention remain later engineering and billing decisions.

## 76. Module 14 — release manifest
PR #82 adds `scripts/release/generate-release-manifest.py`. It creates a JSON artifact tied to the exact checked-out commit and Git tree.

## 77. Module 14 hashed inputs
POMs, package files, lockfiles, Dockerfiles, pipeline YAML, migrations, infrastructure assets, and release scripts are included with size and SHA-256.

## 78. Module 14 confidentiality
The artifact records paths, sizes, hashes, repository URL, commit, tree, timestamp, and dirty state. It never embeds file contents or secret values.

## 79. Module 14 reproducibility
The pipeline sets `SOURCE_DATE_EPOCH` to the commit timestamp. The manifest content hash is calculated from canonical JSON data.

## 80. Module 14 artifact handling
The manifest is published as `craves-release-manifest` or, through the orchestrator, `craves-release-readiness-manifest`. It is not committed automatically.

## 81. Module 15 — orchestrator
PR #83 adds `azure-pipelines-release-readiness-orchestrator.yml` and `scripts/release/verify-release-gate-inventory.sh`.

## 82. Module 15 inventory gate
The inventory script requires all fourteen preceding scripts and pipelines to exist and be non-empty. It also scans the release-gate layer for obvious deployment mutations.

## 83. Module 15 source stage
The first stage validates inventory, YAML, secrets, Dockerfiles, Flyway migrations, APIM assets, and rollback readiness.

## 84. Module 15 build stage
Java and Node jobs run independently after source integrity succeeds. Both must pass before the PR stack is inspected.

## 85. Module 15 PR stage
The orchestrator checks PR #68 through PR #82 by default. The range can be overridden when a later release batch is prepared.

## 86. Module 15 Azure stage
Container App preflight, fail-closed controls, HTTPS health smoke checks, and observability checks run through the Azure service connection using read operations only.

## 87. Module 15 evidence stage
The final stage generates and publishes the immutable manifest only after all prior stages succeed.

## 88. Module 15 deployment boundary
No deployment pipeline is invoked by name or template. There is no Docker push, ACR login, Container App update, APIM mutation, database connection, or Service Bus send.

## 89. Pipeline registration order
Register the fifteen YAML files in PR order, beginning with the PR-stack validator and ending with the orchestrator. Registration is an Azure DevOps UI/manual action.

## 90. First execution order
Run each standalone gate against its exact feature-branch head first. This isolates failures and avoids diagnosing multiple gates inside the orchestrator.

## 91. Orchestrator execution
Run the orchestrator only after all standalone gates have individually passed and their required manual variables are configured.

## 92. Expected Node remediation
Generate reviewed lockfiles for `apps/customer-web-next` and `apps/mobile/customer-app`, commit them to the correct stacked branches, replace legacy `npm install` paths with `npm ci`, and rerun Node-related gates.

## 93. Expected rollback remediation
Any deployment pipeline lacking previous-image capture or a matching rollback path must be hardened before it is eligible for execution.

## 94. Expected Docker remediation
Any Dockerfile still running as root or using an unpinned image must be corrected and locally tested before its service deployment pipeline.

## 95. Expected observability remediation
If the managed environment is not linked to Log Analytics/Azure Monitor, warn about billing and obtain explicit approval before creating or modifying paid observability resources.

## 96. Manual Firebase work
Phone Authentication, authorized web domains, test numbers, Android/iOS app registration, native configuration downloads, SHA fingerprints, APNs, and store-signing configuration remain manual console tasks.

## 97. Manual Cashfree work
Merchant KYC, API credentials, webhook registration, production mode, allowed domains, native configuration, and sandbox/production payment tests remain manual Cashfree tasks. Credentials must go to Key Vault or secret pipeline variables.

## 98. Manual Azure/APIM work
Pipeline registration, service-connection validation, APIM operation rollout, Container App deployment, scaling, networking, Key Vault access, and billable resource changes remain deferred.

## 99. Merge discipline
Do not merge a child before its parent. For each PR: run CI, repair failures on that branch, revalidate the exact head, merge, then rebase or synchronize the next child if required.

## 100. Final handoff state
All fifteen release-readiness modules are code-only and draft. No pipeline in this batch has been executed. Runtime, APIM, databases, Firebase, Cashfree, DNS, mobile stores, and provider integrations remain unchanged.
