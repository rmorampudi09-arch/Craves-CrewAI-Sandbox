#!/usr/bin/env bash
set -euo pipefail
set +x

RESOURCE_GROUP=${1:?resource group required}
DATABASE_NAME=${2:-craves_business_db}
REQUIRED_EXTENSION=${3:-pgcrypto}

command -v az >/dev/null 2>&1 || { echo 'ERROR: Azure CLI is required.' >&2; exit 1; }

mapfile -t SERVERS < <(az postgres flexible-server list \
  --resource-group "$RESOURCE_GROUP" \
  --query '[].name' \
  --output tsv \
  --only-show-errors)

MATCHES=()
for server in "${SERVERS[@]}"; do
  [[ -n "$server" ]] || continue
  if az postgres flexible-server db show \
      --resource-group "$RESOURCE_GROUP" \
      --server-name "$server" \
      --name "$DATABASE_NAME" \
      --output none \
      --only-show-errors 2>/dev/null; then
    MATCHES+=("$server")
  fi
done

if (( ${#MATCHES[@]} != 1 )); then
  echo "ERROR: Expected exactly one PostgreSQL Flexible Server in $RESOURCE_GROUP hosting database $DATABASE_NAME; found ${#MATCHES[@]}." >&2
  if (( ${#MATCHES[@]} > 0 )); then
    printf 'Matches: %s\n' "${MATCHES[*]}" >&2
  fi
  exit 1
fi

SERVER=${MATCHES[0]}
CURRENT=$(az postgres flexible-server parameter show \
  --resource-group "$RESOURCE_GROUP" \
  --server-name "$SERVER" \
  --name azure.extensions \
  --query value \
  --output tsv \
  --only-show-errors)

NORMALIZED=",${CURRENT,,},"
NORMALIZED=${NORMALIZED// /}

if [[ "$NORMALIZED" != *",${REQUIRED_EXTENSION,,},"* ]]; then
  echo "ERROR: PostgreSQL Flexible Server '$SERVER' hosts '$DATABASE_NAME' but '$REQUIRED_EXTENSION' is not allow-listed in azure.extensions." >&2
  echo "Current azure.extensions: ${CURRENT:-<empty>}" >&2
  echo "Manual Azure configuration is required before deployment. The pipeline will not change server parameters automatically." >&2
  exit 1
fi

VERSION=$(az postgres flexible-server show \
  --resource-group "$RESOURCE_GROUP" \
  --name "$SERVER" \
  --query version \
  --output tsv \
  --only-show-errors)

echo "Subscription PostgreSQL prerequisite check: PASS"
echo "Server: $SERVER"
echo "Database: $DATABASE_NAME"
echo "PostgreSQL version: ${VERSION:-unknown}"
echo "Required extension allow-listed: $REQUIRED_EXTENSION"
