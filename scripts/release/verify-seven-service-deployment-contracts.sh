#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HELPER="$ROOT/scripts/release/deploy-single-service-preserve-runtime.sh"
fail() { echo "ERROR: $*" >&2; exit 1; }

command -v python3 >/dev/null || fail "python3 is required"
[[ -s "$HELPER" ]] || fail "runtime-preserving deployment helper is missing"

python3 - "$ROOT" "$HELPER" <<'PY'
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
helper = Path(sys.argv[2])

pipelines = {
    "auth": root / "azure-pipelines-auth-service.yml",
    "user-chef": root / "azure-pipelines-user-chef-service.yml",
    "catalog": root / "azure-pipelines-catalog-service.yml",
    "order": root / "azure-pipelines-order-service.yml",
    "subscription": root / "azure-pipelines-subscription-service.yml",
    "integration": root / "azure-pipelines-integration-service.yml",
    "notification": root / "azure-pipelines-notification-service.yml",
}

common_pipeline_required = (
    "trigger: none",
    "pr: none",
    "versionSpec: '21'",
    "mvn -B -ntp clean verify",
    "docker build --pull",
    "docker push",
    "AZURE_SERVICE_CONNECTION",
)

shared_helper_services = {
    "auth",
    "user-chef",
    "order",
    "subscription",
    "integration",
    "notification",
}

forbidden_pipeline_patterns = (
    r"--set-env-vars\b",
    r"--replace-env-vars\b",
    r"\bcontainerapp\s+secret\s+set\b",
    r"\bcontainerapp\s+ingress\s+update\b",
    r"--min-replicas\b",
    r"--max-replicas\b",
)

credential_macro_pattern = re.compile(
    r"\$\((?:"
    r"POSTGRES_[A-Z0-9_]*(?:PASSWORD|SECRET)|"
    r"FIREBASE_SERVICE_ACCOUNT[A-Z0-9_]*|"
    r"CRAVES_JWT_[A-Z0-9_]*|"
    r"CRAVES_INTERNAL_[A-Z0-9_]*|"
    r"ACS_[A-Z0-9_]*(?:SECRET|KEY|CONNECTION)|"
    r"CASHFREE_[A-Z0-9_]*(?:SECRET|KEY)|"
    r"BORZO_[A-Z0-9_]*(?:TOKEN|SECRET|KEY)"
    r")\)"
)

for service, path in pipelines.items():
    if not path.is_file():
        raise SystemExit(f"ERROR: Missing deployment pipeline for {service}: {path.name}")

    text = path.read_text(encoding="utf-8")

    for token in common_pipeline_required:
        if token not in text:
            raise SystemExit(
                f"ERROR: {path.name} lacks required deployment contract token: {token}"
            )

    if service in shared_helper_services:
        if "scripts/release/deploy-single-service-preserve-runtime.sh" not in text:
            raise SystemExit(
                f"ERROR: {path.name} must use the shared runtime-preserving deployment helper"
            )
    else:
        catalog_required = (
            "Runtime environment preserved: YES",
            "secret_metadata_hash",
            "environment_hash",
            "--image",
            "/actuator/health/readiness",
        )
        for token in catalog_required:
            if token not in text:
                raise SystemExit(
                    f"ERROR: {path.name} lacks Catalog runtime-preservation token: {token}"
                )

    for pattern in forbidden_pipeline_patterns:
        if re.search(pattern, text):
            raise SystemExit(
                f"ERROR: {path.name} contains forbidden routine-deployment mutation: {pattern}"
            )

    if credential_macro_pattern.search(text):
        raise SystemExit(
            f"ERROR: {path.name} consumes a credential-value pipeline macro; "
            "routine deployments must reuse existing Key Vault-backed runtime state"
        )

    if "-DskipTests" in text or "maven.test.skip" in text:
        raise SystemExit(f"ERROR: {path.name} skips tests")

    print(f"DEPLOYMENT_CONTRACT_OK {service}={path.name}")

helper_text = helper.read_text(encoding="utf-8")
helper_required = (
    "runtime_template_hash",
    "configuration_hash",
    "identity_hash",
    "secret_metadata_hash",
    "verify_active_secret_refs_are_key_vault_backed",
    "az containerapp update",
    "--image",
    "rollback()",
    "/actuator/health/liveness",
    "/actuator/health/readiness",
    "Credential values read:     NO",
    "Credential values changed:  NO",
)
for token in helper_required:
    if token not in helper_text:
        raise SystemExit(
            f"ERROR: runtime-preserving deployment helper lacks required token: {token}"
        )

for pattern in forbidden_pipeline_patterns[:3]:
    if re.search(pattern, helper_text):
        raise SystemExit(
            f"ERROR: runtime-preserving helper contains forbidden runtime/secret mutation: {pattern}"
        )

if "-DskipTests" in helper_text or "maven.test.skip" in helper_text:
    raise SystemExit("ERROR: runtime-preserving helper unexpectedly contains test-skip controls")

print(
    "SUCCESS: All seven service pipelines build/test immutable images and preserve "
    "the existing Key Vault-backed runtime configuration during routine deployment."
)
PY
