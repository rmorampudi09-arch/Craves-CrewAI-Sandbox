#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INVENTORY="$ROOT/config/production/container-app-secret-names.json"
fail() { echo "ERROR: $*" >&2; exit 1; }
command -v python3 >/dev/null || fail "python3 is required"
[[ -f "$INVENTORY" ]] || fail "Container App secret-name inventory is missing"

python3 - "$ROOT" "$INVENTORY" <<'PY'
import json
import re
import sys
from pathlib import Path

root = Path(sys.argv[1])
inventory_path = Path(sys.argv[2])
data = json.loads(inventory_path.read_text(encoding="utf-8"))
maximum = data.get("maximumNameLength")
names = data.get("names")
if data.get("schemaVersion") != 1 or maximum != 20 or not isinstance(names, dict) or not names:
    raise SystemExit("ERROR: Invalid Container App secret-name inventory")
pattern = re.compile(r"^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$")
values = list(names.values())
if len(values) != len(set(values)):
    raise SystemExit("ERROR: Container App secret names are not unique")
for logical_name, secret_name in names.items():
    if not isinstance(secret_name, str) or not pattern.fullmatch(secret_name):
        raise SystemExit(f"ERROR: Invalid secret name for {logical_name}: {secret_name!r}")
    if "--" in secret_name or len(secret_name) > maximum:
        raise SystemExit(f"ERROR: Secret name exceeds Azure CLI constraints for {logical_name}: {secret_name}")
    print(f"SECRET_NAME_OK {logical_name}={secret_name}")

# Parse only literal keys passed to `az containerapp secret set --secrets`.
# Existing Key Vault-backed references provisioned through infrastructure are not renamed here.
assignment = re.compile(r"(?:^|\s)([a-z0-9][a-z0-9-]*)=")
for pipeline in sorted(root.glob("azure-pipelines*.yml")):
    lines = pipeline.read_text(encoding="utf-8").splitlines()
    index = 0
    while index < len(lines):
        if "az containerapp secret set" not in lines[index]:
            index += 1
            continue
        command_lines = [lines[index].strip()]
        while command_lines[-1].rstrip().endswith("\\") and index + 1 < len(lines):
            index += 1
            command_lines.append(lines[index].strip())
        command = " ".join(part.rstrip("\\").strip() for part in command_lines)
        if "--secrets" not in command:
            raise SystemExit(f"ERROR: Secret-set command lacks --secrets in {pipeline.name}")
        secret_part = command.split("--secrets", 1)[1]
        secret_part = re.split(r"\s+(?:-o|--output)\s+", secret_part, maxsplit=1)[0]
        keys = assignment.findall(secret_part)
        if not keys:
            raise SystemExit(f"ERROR: No literal secret keys were parsed in {pipeline.name}: {command}")
        for name in keys:
            if len(name) > maximum or not pattern.fullmatch(name) or "--" in name:
                raise SystemExit(f"ERROR: Invalid CLI-managed secret key {name} in {pipeline.name}")
            print(f"PIPELINE_SECRET_OK {pipeline.name}:{name}")
        index += 1

required = {
    "azure-pipelines-auth-service.yml": {
        names["databasePassword"], names["firebaseAdminJson"],
        names["jwtPrivatePem"], names["internalServiceSecret"]
    },
    "azure-pipelines-user-chef-service.yml": {
        names["databasePassword"], names["jwtVerificationPem"]
    },
    "azure-pipelines-catalog-service.yml": {
        names["databasePassword"], names["jwtVerificationPem"]
    },
    "azure-pipelines-order-service.yml": {
        names["databasePassword"], names["jwtVerificationPem"]
    },
    "azure-pipelines-subscription-service.yml": {
        names["databasePassword"], names["jwtVerificationPem"], names["internalServiceSecret"]
    },
    "azure-pipelines-integration-service.yml": {
        names["databasePassword"], names["jwtVerificationPem"], names["internalServiceSecret"]
    },
    "azure-pipelines-notification-service.yml": {
        names["databasePassword"], names["jwtVerificationPem"], names["internalServiceSecret"]
    }
}
for filename, required_names in required.items():
    path = root / filename
    if not path.is_file():
        raise SystemExit(f"ERROR: Required deployment pipeline is missing: {filename}")
    text = path.read_text(encoding="utf-8")
    for secret_name in required_names:
        if f"{secret_name}=" not in text or f"secretref:{secret_name}" not in text:
            raise SystemExit(f"ERROR: {filename} does not consistently create and reference {secret_name}")

for forbidden in (
    "spring-datasource-password=",
    "firebase-service-account-json-base64=",
    "craves-jwt-private-key-pem-base64=",
    "craves-jwt-verification-pem-base64=",
    "craves-internal-service-secret=",
):
    for filename in required:
        if forbidden in (root / filename).read_text(encoding="utf-8"):
            raise SystemExit(f"ERROR: Overlength CLI secret key remains in {filename}: {forbidden[:-1]}")

print("SUCCESS: CLI-managed Container App secret names satisfy the current Azure CLI constraints.")
PY
