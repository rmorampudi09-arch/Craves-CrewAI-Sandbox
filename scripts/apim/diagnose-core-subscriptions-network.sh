#!/usr/bin/env bash
set -euo pipefail
set +x

RG="${RG:-rg-craves-prodlow-centralindia}"
APIM="${APIM:-apim-craves-prodlow-l3ing6}"
SUB_APP="${SUB_APP:-ca-craves-subscription-service-p}"
APIM_SERVICE_API_VERSION="2024-05-01"

for tool in az jq python3; do
  command -v "$tool" >/dev/null || {
    echo "NETWORK_DIAGNOSTIC_ERROR: $tool is required" >&2
    exit 0
  }
done

SUBSCRIPTION_ID="$(az account show --query id -o tsv --only-show-errors 2>/dev/null || true)"
if [[ -z "$SUBSCRIPTION_ID" ]]; then
  echo "NETWORK_DIAGNOSTIC_ERROR: Azure subscription ID could not be resolved" >&2
  exit 0
fi

APIM_JSON="$(az apim show --resource-group "$RG" --name "$APIM" -o json --only-show-errors 2>/dev/null || true)"
SUB_APP_JSON="$(az containerapp show --resource-group "$RG" --name "$SUB_APP" -o json --only-show-errors 2>/dev/null || true)"
if [[ -z "$APIM_JSON" || -z "$SUB_APP_JSON" ]]; then
  echo "NETWORK_DIAGNOSTIC_ERROR: Azure management-plane resource discovery failed" >&2
  exit 0
fi

APIM_GATEWAY_URL="$(jq -r '.gatewayUrl // .properties.gatewayUrl // ""' <<<"$APIM_JSON")"
APIM_HOST="${APIM_GATEWAY_URL#https://}"
APIM_HOST="${APIM_HOST%%/*}"
APIM_STATE="$(jq -r '.provisioningState // .properties.provisioningState // ""' <<<"$APIM_JSON")"
APIM_PUBLIC_NETWORK="$(jq -r '.publicNetworkAccess // .properties.publicNetworkAccess // ""' <<<"$APIM_JSON")"
APIM_VNET_TYPE="$(jq -r '.virtualNetworkType // .properties.virtualNetworkType // ""' <<<"$APIM_JSON")"
APIM_DISABLE_GATEWAY="$(jq -r '.disableGateway // .properties.disableGateway // false' <<<"$APIM_JSON")"
APIM_LOCATION="$(jq -r '.location // ""' <<<"$APIM_JSON")"
APIM_SKU="$(jq -r '.sku.name // ""' <<<"$APIM_JSON")"
APIM_CAPACITY="$(jq -r '.sku.capacity // ""' <<<"$APIM_JSON")"
APIM_PUBLIC_IPS="$(jq -r '(.publicIPAddresses // .properties.publicIPAddresses // []) | join(",")' <<<"$APIM_JSON")"
APIM_PRIVATE_IPS="$(jq -r '(.privateIPAddresses // .properties.privateIPAddresses // []) | join(",")' <<<"$APIM_JSON")"
APIM_PRIVATE_ENDPOINTS="$(jq '(.privateEndpointConnections // .properties.privateEndpointConnections // []) | length' <<<"$APIM_JSON")"

SUB_FQDN="$(jq -r '.properties.configuration.ingress.fqdn // ""' <<<"$SUB_APP_JSON")"
SUB_EXTERNAL="$(jq -r '.properties.configuration.ingress.external // false' <<<"$SUB_APP_JSON")"
SUB_RUNNING="$(jq -r '.properties.runningStatus // ""' <<<"$SUB_APP_JSON")"
SUB_READY="$(jq -r '.properties.latestReadyRevisionName // ""' <<<"$SUB_APP_JSON")"
SUB_TARGET_PORT="$(jq -r '.properties.configuration.ingress.targetPort // 0' <<<"$SUB_APP_JSON")"
SUB_TRANSPORT="$(jq -r '.properties.configuration.ingress.transport // ""' <<<"$SUB_APP_JSON")"
SUB_ENVIRONMENT_ID="$(jq -r '.properties.environmentId // .properties.managedEnvironmentId // ""' <<<"$SUB_APP_JSON")"
SUB_IP_RESTRICTIONS="$(jq -c '.properties.configuration.ingress.ipSecurityRestrictions // []' <<<"$SUB_APP_JSON")"

echo "============================================================"
echo "READ-ONLY NETWORK LAYER DIAGNOSTICS"
echo "APIM: host=$APIM_HOST state=$APIM_STATE publicNetworkAccess=${APIM_PUBLIC_NETWORK:-default} virtualNetworkType=${APIM_VNET_TYPE:-None} disableGateway=$APIM_DISABLE_GATEWAY"
echo "APIM: location=$APIM_LOCATION sku=$APIM_SKU capacity=${APIM_CAPACITY:-n/a} publicIPs=${APIM_PUBLIC_IPS:-none} privateIPs=${APIM_PRIVATE_IPS:-none} privateEndpointConnections=$APIM_PRIVATE_ENDPOINTS"
echo "Subscription Service: fqdn=$SUB_FQDN external=$SUB_EXTERNAL running=$SUB_RUNNING readyRevision=$SUB_READY targetPort=$SUB_TARGET_PORT transport=${SUB_TRANSPORT:-auto}"
echo "Subscription Service: environmentId=${SUB_ENVIRONMENT_ID:-unknown} ipSecurityRestrictions=$(jq 'length' <<<"$SUB_IP_RESTRICTIONS")"
if [[ "$(jq 'length' <<<"$SUB_IP_RESTRICTIONS")" -gt 0 ]]; then
  jq -r '.[] | "  ingress-rule name=\(.name // "") action=\(.action // "") range=\(.ipAddressRange // "")"' <<<"$SUB_IP_RESTRICTIONS"
fi

echo "APIM internal dependency network status:"
APIM_RESOURCE_BASE="https://management.azure.com/subscriptions/${SUBSCRIPTION_ID}/resourceGroups/${RG}/providers/Microsoft.ApiManagement/service/${APIM}"
NETWORK_STATUS="$(az rest --method get --url "${APIM_RESOURCE_BASE}/networkstatus?api-version=${APIM_SERVICE_API_VERSION}" -o json --only-show-errors 2>/tmp/craves-networkstatus.err || true)"
if [[ -n "$NETWORK_STATUS" ]]; then
  jq -r '
    .[]? as $region
    | "  location=\($region.location // "unknown") dnsServers=\(($region.networkStatus.dnsServers // []) | join(","))",
      (($region.networkStatus.connectivityStatus // [])[]?
        | select((.status // "") != "Success")
        | "    NON-SUCCESS name=\(.name // "") status=\(.status // "") optional=\(.isOptional // false) error=\(.error // "")")
  ' <<<"$NETWORK_STATUS" || true
else
  echo "  unavailable"
  sed -n '1,6p' /tmp/craves-networkstatus.err >&2 || true
fi
rm -f /tmp/craves-networkstatus.err

network_probe() {
  local HOST="$1"
  local LABEL="$2"
  if [[ -z "$HOST" ]]; then
    echo "NETWORK_PROBE $LABEL: no hostname"
    return 0
  fi

  echo "------------------------------------------------------------"
  echo "NETWORK_PROBE $LABEL host=$HOST"
  python3 - "$HOST" <<'PY'
import ipaddress
import socket
import ssl
import sys
import time

host = sys.argv[1]
try:
    infos = socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
except Exception as exc:
    print(f"DNS_RESULT=FAIL error={type(exc).__name__}:{exc}")
    print("NETWORK_CLASSIFICATION=DNS_RESOLUTION_FAILURE")
    raise SystemExit(0)

unique=[]
for info in infos:
    ip=info[4][0]
    if ip not in unique:
        unique.append(ip)

if not unique:
    print("DNS_RESULT=FAIL error=no_addresses")
    print("NETWORK_CLASSIFICATION=DNS_RESOLUTION_FAILURE")
    raise SystemExit(0)

private_only=True
for ip in unique:
    try:
        addr=ipaddress.ip_address(ip)
        private=addr.is_private or addr.is_loopback or addr.is_link_local
    except ValueError:
        private=False
    if not private:
        private_only=False
    print(f"DNS_ADDRESS={ip} scope={'private' if private else 'public'}")

if private_only:
    print("NETWORK_CLASSIFICATION=PRIVATE_DNS_OR_PRIVATE_ENDPOINT_PATH")

connected=False
for family, socktype, proto, canonname, sockaddr in infos:
    ip=sockaddr[0]
    sock=socket.socket(family, socket.SOCK_STREAM)
    sock.settimeout(5)
    started=time.monotonic()
    try:
        sock.connect(sockaddr)
        elapsed=time.monotonic()-started
        print(f"TCP443_RESULT=PASS ip={ip} seconds={elapsed:.3f}")
        connected=True
        try:
            context=ssl.create_default_context()
            with context.wrap_socket(sock, server_hostname=host) as tls:
                cert=tls.getpeercert()
                print(f"TLS_RESULT=PASS version={tls.version()} notAfter={cert.get('notAfter','unknown')}")
            sock=None
        except Exception as exc:
            print(f"TLS_RESULT=FAIL ip={ip} error={type(exc).__name__}:{exc}")
            print("NETWORK_CLASSIFICATION=TLS_HANDSHAKE_OR_CERTIFICATE_FAILURE")
        break
    except Exception as exc:
        elapsed=time.monotonic()-started
        print(f"TCP443_RESULT=FAIL ip={ip} seconds={elapsed:.3f} error={type(exc).__name__}:{exc}")
    finally:
        if sock is not None:
            try:
                sock.close()
            except Exception:
                pass

if not connected:
    print("NETWORK_CLASSIFICATION=TCP_443_CONNECTIVITY_FAILURE")
elif not private_only:
    print("NETWORK_CLASSIFICATION=TCP_443_REACHABLE")
PY
}

network_probe "$APIM_HOST" "APIM_GATEWAY"
if [[ "$SUB_EXTERNAL" == "true" ]]; then
  network_probe "$SUB_FQDN" "SUBSCRIPTION_SERVICE"
else
  echo "NETWORK_PROBE SUBSCRIPTION_SERVICE: skipped because ingress.external=false"
  echo "NETWORK_CLASSIFICATION_SUBSCRIPTION_SERVICE=INTERNAL_INGRESS"
fi

echo "============================================================"
echo "END READ-ONLY NETWORK LAYER DIAGNOSTICS"
echo "============================================================"
exit 0
