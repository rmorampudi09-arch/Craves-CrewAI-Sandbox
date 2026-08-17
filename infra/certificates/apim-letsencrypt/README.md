# Craves APIM Let's Encrypt Certificate Automation

This module issues and renews the TLS certificate for `api.craves.in`, validates ownership through the delegated Azure DNS zone, and imports each new certificate as a new version of `craves-api-tls` in Azure Key Vault.

The pipeline does **not** publish a PFX artifact and does not write the private key to the repository. Certificate files exist only in a permission-restricted temporary directory on the Microsoft-hosted build agent and are deleted when the script exits.

## Architecture

```text
GoDaddy parent zone: craves.in
  └─ _acme-challenge.api.craves.in NS delegation
       └─ Azure DNS zone: _acme-challenge.api.craves.in
            └─ Temporary ACME TXT record created and removed by acme.sh

Azure DevOps service connection
  ├─ DNS Zone Contributor on the delegated Azure DNS zone
  └─ Key Vault Certificates Officer on kvcravesprodlowl3ing6

Let's Encrypt
  └─ RSA 2048 certificate for api.craves.in

Azure Key Vault
  └─ Certificate name: craves-api-tls
       └─ New version on each successful renewal

Azure API Management
  └─ Gateway custom domain api.craves.in
       └─ Versionless Key Vault secret reference
```

## Files

```text
azure-pipelines-apim-letsencrypt-certificate.yml
infra/certificates/apim-letsencrypt/
├── README.md
└── renew-apim-certificate.sh
```

## Existing prerequisites

The following production prerequisites must remain in place:

- GoDaddy CNAME `api` points to `api.craves.in`.
- GoDaddy delegates `_acme-challenge.api.craves.in` to the four Azure DNS nameservers assigned to the child zone.
- Azure DNS zone `_acme-challenge.api.craves.in` exists in `rg-craves-prodlow-centralindia`.
- Key Vault `kvcravesprodlowl3ing6` uses Azure RBAC.
- APIM `apim-craves-prodlow-l3ing6` has a system-assigned managed identity.
- APIM managed identity has `Key Vault Secrets User` on the Key Vault.
- Azure DevOps service principal has `DNS Zone Contributor` on the delegated DNS zone.
- Azure DevOps service principal has `Key Vault Certificates Officer` on the Key Vault.
- Azure DevOps variable `AZURE_SERVICE_CONNECTION` contains `Craves-Dev-Service-Connection`.

Do not store a Let's Encrypt private key, PFX password, Azure access token, or Azure service-principal secret in Azure DevOps variables.

## Pipeline creation

Create an Azure DevOps YAML pipeline using:

```text
/azure-pipelines-apim-letsencrypt-certificate.yml
```

The YAML contains a weekly schedule:

```text
Sunday 02:30 UTC / Sunday 08:00 IST
```

Scheduled runs check the current certificate and exit without contacting Let's Encrypt when more than 30 days remain.

## Initial issuance

Queue the pipeline manually with:

```text
confirmProductionCertificateOperation: true
forceIssue: false
letsEncryptContactEmail: support@craves.in
```

The initial run should finish with:

```text
CERTIFICATE_CHANGED=true
CERTIFICATE_NAME=craves-api-tls
APIM_BINDING_REQUIRED=true
```

`forceIssue` should normally remain `false`. Set it to `true` only for a controlled recovery after confirming that Let's Encrypt rate limits will not be affected.

## One-time APIM custom-domain binding

After the initial certificate exists in Key Vault:

1. Open Azure Portal.
2. Open API Management service `apim-craves-prodlow-l3ing6`.
3. Open **Custom domains**.
4. Add endpoint type **Gateway**.
5. Set hostname to `api.craves.in`.
6. Select **Key Vault** as the certificate source.
7. Select Key Vault `kvcravesprodlowl3ing6`.
8. Select certificate `craves-api-tls`.
9. Select the APIM system-assigned managed identity as the client identity.
10. Keep client-certificate negotiation off.
11. Add and save the APIM change.

Use the versionless Key Vault secret reference:

```text
https://kvcravesprodlowl3ing6.vault.azure.net/secrets/craves-api-tls
```

A versionless reference is required so APIM can synchronize newer Key Vault certificate versions after scheduled renewal.

## Verification

```bash
openssl s_client \
  -connect api.craves.in:443 \
  -servername api.craves.in \
  </dev/null 2>/dev/null |
openssl x509 \
  -noout \
  -subject \
  -issuer \
  -dates \
  -ext subjectAltName
```

The SAN must include:

```text
DNS:api.craves.in
```

Verify the APIM endpoint:

```bash
curl -sS \
  -o /dev/null \
  -w 'HTTP status: %{http_code}\nTLS verification: %{ssl_verify_result}\n' \
  https://api.craves.in/
```

A `404` at `/` is acceptable when APIM has no root operation. `TLS verification` must be `0`.

## Renewal behavior

The scheduled pipeline runs weekly and follows this logic:

1. Validate the Azure subscription, DNS delegation, APIM default gateway and Key Vault.
2. Download only the public portion of the current Key Vault certificate.
3. Exit without issuance when the certificate remains valid for more than 30 days.
4. Use a short-lived Azure Resource Manager bearer token for the DNS-01 challenge.
5. Issue an RSA 2048 certificate from Let's Encrypt using pinned `acme.sh` version `3.1.4`.
6. Validate the private key, chain and `api.craves.in` SAN.
7. Build a password-protected PFX inside the temporary agent directory.
8. Import the PFX as a new Key Vault certificate version.
9. Compare the imported certificate SHA-256 fingerprint with the issued certificate.
10. Delete all temporary certificate material.

When the APIM binding uses the versionless Key Vault secret reference, APIM normally obtains a newer certificate version within four hours. Use **Sync certificates** in the APIM Custom domains page when an immediate refresh is required.

## Local static validation

The issuance script requires an authenticated Azure environment and should not be executed from an untrusted workstation. Static checks can be run locally:

```bash
bash -n infra/certificates/apim-letsencrypt/renew-apim-certificate.sh
shellcheck infra/certificates/apim-letsencrypt/renew-apim-certificate.sh
```

## Manual intervention required

- Create the Azure DevOps pipeline from the YAML path.
- Run the first issuance with the confirmation parameter.
- Complete the one-time APIM Key Vault custom-domain binding.
- Verify the live TLS certificate and an actual API route.
- Keep the existing GoDaddy CNAME and ACME NS delegation records.

## Rollback

The default APIM gateway remains available at:

```text
https://api.craves.in
```

If the custom-domain binding fails, remove only `api.craves.in` from APIM Custom domains. Do not delete the default APIM hostname, the Key Vault certificate, the GoDaddy CNAME, or the ACME DNS delegation while investigating.

## Operational risks

- Let's Encrypt rate limits apply. Do not repeatedly use forced issuance.
- A failed scheduled run must be investigated before the existing certificate enters its final validity days.
- Key Vault public access is currently enabled. Network hardening should be handled as a separate change because it can block APIM certificate retrieval and pipeline imports if applied incorrectly.
- The Azure DevOps service principal currently has a pre-existing `Key Vault Secrets Officer` role in addition to the certificate role. This automation does not require that broader role. Review and remove it later only after confirming that no other Craves pipeline depends on it.
- APIM certificate synchronization is not instantaneous. Keep at least a 30-day renewal window and monitor the certificate expiry date.
