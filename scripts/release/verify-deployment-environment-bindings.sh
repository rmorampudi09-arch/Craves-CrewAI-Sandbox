#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HELPER="$ROOT/scripts/release/deploy-single-service-preserve-runtime.sh"
command -v python3 >/dev/null || { echo 'ERROR: python3 is required' >&2; exit 1; }
[[ -s "$HELPER" ]] || { echo 'ERROR: runtime-preserving deployment helper is missing' >&2; exit 1; }

python3 - "$ROOT" "$HELPER" <<'PY'
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
helper = Path(sys.argv[2])

pairs = {
    "azure-pipelines-auth-service.yml": "services/auth-service",
    "azure-pipelines-user-chef-service.yml": "services/user-chef-service",
    "azure-pipelines-catalog-service.yml": "services/catalog-service",
    "azure-pipelines-order-service.yml": "services/order-service",
    "azure-pipelines-subscription-service.yml": "services/subscription-service",
    "azure-pipelines-integration-service.yml": "services/integration-service",
    "azure-pipelines-notification-service.yml": "services/notification-service",
}

forbidden = re.compile(
    r"--set-env-vars\b|--replace-env-vars\b|"
    r"\bcontainerapp\s+secret\s+set\b|"
    r"\bcontainerapp\s+ingress\s+update\b|"
    r"--min-replicas\b|--max-replicas\b"
)

credential_macro = re.compile(
    r"\$\((?:POSTGRES_[A-Z0-9_]*(?:PASSWORD|SECRET)|"
    r"FIREBASE_SERVICE_ACCOUNT[A-Z0-9_]*|"
    r"CRAVES_JWT_[A-Z0-9_]*|"
    r"CRAVES_INTERNAL_[A-Z0-9_]*|"
    r"CASHFREE_[A-Z0-9_]*(?:SECRET|KEY)|"
    r"BORZO_[A-Z0-9_]*(?:TOKEN|SECRET|KEY)|"
    r"ACS_[A-Z0-9_]*(?:SECRET|KEY|CONNECTION))\)"
)

problems: list[str] = []

for pipeline_name, service_dir_name in pairs.items():
    pipeline = root / pipeline_name
    service_dir = root / service_dir_name
    app_yml = service_dir / "src/main/resources/application.yml"

    if not pipeline.is_file():
        problems.append(f"Missing deployment pipeline: {pipeline_name}")
        continue
    if not app_yml.is_file():
        problems.append(f"Missing application.yml: {service_dir_name}")
        continue

    pipeline_text = pipeline.read_text(encoding="utf-8")
    app_text = app_yml.read_text(encoding="utf-8", errors="ignore")

    match = forbidden.search(pipeline_text)
    if match:
        problems.append(
            f"{pipeline_name} mutates runtime configuration during routine deployment: "
            f"{match.group(0)}"
        )

    if credential_macro.search(pipeline_text):
        problems.append(
            f"{pipeline_name} consumes credential-value pipeline variables instead of "
            "preserving the existing Key Vault-backed runtime"
        )

    if "SPRING_DATASOURCE_PASSWORD" not in app_text:
        problems.append(
            f"{service_dir_name} does not declare the standard datasource password binding"
        )

    if pipeline_name == "azure-pipelines-catalog-service.yml":
        required = (
            "environment_hash",
            "secret_metadata_hash",
            "Runtime environment preserved: YES",
        )
    else:
        required = (
            "scripts/release/deploy-single-service-preserve-runtime.sh",
        )

    for token in required:
        if token not in pipeline_text:
            problems.append(f"{pipeline_name} lacks runtime-preservation token: {token}")

    print(f"ENVIRONMENT_PRESERVATION_OK {pipeline_name} -> {service_dir_name}")

helper_text = helper.read_text(encoding="utf-8")
for token in (
    "properties.template.containers[0].env",
    "runtime_template_hash",
    "configuration_hash",
    "secret_metadata_hash",
    "verify_active_secret_refs_are_key_vault_backed",
):
    if token not in helper_text:
        problems.append(f"runtime-preserving helper lacks environment contract token: {token}")

if forbidden.search(helper_text):
    problems.append("runtime-preserving helper contains an environment/secret/ingress/scaling mutation")

if problems:
    print(
        f"ERROR: {len(problems)} deployment environment preservation issue(s) found:",
        file=sys.stderr,
    )
    for problem in problems:
        print(f"  - {problem}", file=sys.stderr)
    raise SystemExit(1)

print(
    "SUCCESS: Routine service deployments preserve the existing Container App "
    "environment and Key Vault secret references instead of reconstructing them."
)
PY
