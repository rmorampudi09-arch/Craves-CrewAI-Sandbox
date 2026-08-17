#!/usr/bin/env bash
set -euo pipefail

: "${CRAVES_BASE_URL:=https://craves.in}"
: "${CRAVES_TEST_LATITUDE:=17.4483}"
: "${CRAVES_TEST_LONGITUDE:=78.3915}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

for command in curl jq; do
  command -v "$command" >/dev/null 2>&1 || fail "$command is required"
done

BASE_URL="${CRAVES_BASE_URL%/}"
ORIGIN="$BASE_URL"
BODY_FILE="/tmp/craves-location-runtime-smoke-body.json"
HEADERS_FILE="/tmp/craves-location-runtime-smoke-headers.txt"
rm -f "$BODY_FILE" "$HEADERS_FILE"

HTTP_CODE="$(curl \
  --silent \
  --show-error \
  --location \
  --max-time 30 \
  --dump-header "$HEADERS_FILE" \
  --output "$BODY_FILE" \
  --write-out '%{http_code}' \
  --request POST \
  --header "Origin: $ORIGIN" \
  --header 'Content-Type: application/json' \
  --header 'Accept: application/json' \
  --data "$(jq -nc \
    --argjson latitude "$CRAVES_TEST_LATITUDE" \
    --argjson longitude "$CRAVES_TEST_LONGITUDE" \
    '{latitude:$latitude,longitude:$longitude}')" \
  "$BASE_URL/api/location/reverse-geocode")"

[[ "$HTTP_CODE" == "200" ]] || {
  cat "$BODY_FILE" >&2 || true
  fail "Live reverse-geocoding BFF returned HTTP $HTTP_CODE"
}

jq -e '
  (.formattedAddress | type == "string" and length > 0) and
  (.preciseHouseNumber | type == "boolean") and
  ((has("latitude") or has("longitude")) | not)
' "$BODY_FILE" >/dev/null || {
  cat "$BODY_FILE" >&2
  fail "Live reverse-geocoding response did not match the sanitized Craves contract"
}

CACHE_CONTROL="$(tr -d '\r' < "$HEADERS_FILE" | awk -F': ' 'tolower($1)=="cache-control" {print tolower($2)}' | tail -1)"
[[ "$CACHE_CONTROL" == *"no-store"* ]] || fail "Reverse-geocoding response must be no-store"

FORMATTED_ADDRESS="$(jq -r '.formattedAddress' "$BODY_FILE")"
AREA="$(jq -r '.area // empty' "$BODY_FILE")"
CITY="$(jq -r '.city // empty' "$BODY_FILE")"
DISTRICT="$(jq -r '.district // empty' "$BODY_FILE")"
STATE="$(jq -r '.state // empty' "$BODY_FILE")"
POSTAL_CODE="$(jq -r '.postalCode // empty' "$BODY_FILE")"

cat <<EOF
Live Craves reverse-geocoding smoke passed.
Base URL: $BASE_URL
Formatted address: $FORMATTED_ADDRESS
Area: ${AREA:-provider-not-returned}
City: ${CITY:-provider-not-returned}
District: ${DISTRICT:-provider-not-returned}
State: ${STATE:-provider-not-returned}
Pincode: ${POSTAL_CODE:-provider-not-returned}
Raw latitude/longitude fields exposed to browser response: no
Cache-Control includes no-store: yes
EOF
