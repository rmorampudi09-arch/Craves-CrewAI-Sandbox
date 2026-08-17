# Craves Customer Web Let's Encrypt Certificate Automation

This module issues and renews one RSA 2048 SAN certificate for `craves.in` and `www.craves.in`, validates ownership through two delegated Azure DNS child zones, imports each certificate version into Azure Key Vault as `craves-web-tls`, and imports the versionless Key Vault secret reference into the Craves Container Apps environment.

It does not change the public `@` or `www` traffic records and does not bind the custom domains. Those remain separate controlled cutover steps.

## Architecture

```text
GoDaddy parent zone: craves.in
  ├─ _acme-challenge.craves.in NS delegation
  │    └─ Azure DNS zone: _acme-challenge.craves.in
  └─ _acme-challenge.www.craves.in NS delegation
       └─ Azure DNS zone: _acme-challenge.www.craves.in

Azure DevOps service connection
  ├─ Contributor inherited at subscription scope
  ├─ Key Vault Certificates Officer on kvcravesprodlowl3ing6
  └─ Key Vault Secrets Officer on kvcravesprodlowl3ing6

Let's Encrypt
  └─ RSA 2048 SAN certificate
       ├─ craves.in
       └─ www.craves.in

Azure Key Vault
  └─ Certificate: craves-web-tls
       └─ New version on every successful renewal

Azure Container Apps environment
  └─ cae-craves-prodlow-l3ing6
       ├─ System-assigned managed identity
       ├─ Key Vault Secrets User on the Key Vault
       └─ Versionless Key Vault certificate import
```

## Files

```text
azure-pipelines-web-letsencrypt-certificate.yml
infra/certificates/web-letsencrypt/
├── README.md
└── renew-web-certificate.sh
```

## Existing prerequisites

Keep all of the following in place:

- GoDaddy delegates `_acme-challenge.craves.in` to the four Azure nameservers assigned to that Azure DNS child zone.
- GoDaddy delegates `_acme-challenge.www.craves.in` to the four Azure nameservers assigned to that Azure DNS child zone.
- The existing API delegation `_acme-challenge.api.craves.in` remains unchanged.
- Azure DNS zones `_acme-challenge.craves.in` and `_acme-challenge.www.craves.in` exist in `rg-craves-prodlow-centralindia`.
- Key Vault `kvcravesprodlowl3ing6` uses Azure RBAC.
- Container Apps environment `cae-craves-prodlow-l3ing6` has a system-assigned identity.
- That environment identity has `Key Vault Secrets User` on the Key Vault.
- Azure DevOps variable `AZURE_SERVICE_CONNECTION` contains `Craves-Dev-Service-Connection`.
- `ca-craves-web-prodlow` remains healthy at its generated Container Apps hostname.

Do not store a Let's Encrypt private key, PFX password, Azure bearer token or service-principal secret in Azure DevOps variables.

## Pipeline creation

Create an Azure DevOps YAML pipeline from:

```text
/azure-pipelines-web-letsencrypt-certificate.yml
```

The weekly schedule is Sunday 03:00 UTC / Sunday 08:30 IST. Scheduled runs exit without contacting Let's Encrypt when the current certificate remains valid for more than 30 days.

## Initial issuance

Queue the pipeline manually with:

```text
confirmProductionCertificateOperation: true
forceIssue: false
letsEncryptContactEmail: support@craves.in
```

The first successful run should finish with:

```text
CERTIFICATE_CHANGED=true
CERTIFICATE_NAME=craves-web-tls
CUSTOM_DOMAIN_BINDING_REQUIRED=true
```

The output must also contain a versionless secret ID:

```text
https://kvcravesprodlowl3ing6.vault.azure.net/secrets/craves-web-tls
```

## Security behavior

- The pipeline uses pinned `acme.sh` version `3.1.4`.
- The ACME account, private key and certificate files exist only in a permission-restricted temporary agent directory.
- A short-lived Azure Resource Manager bearer token is used for Azure DNS challenge updates.
- The PFX is protected by a randomly generated password and deleted immediately after Key Vault import.
- The imported certificate fingerprint is compared to the issued certificate fingerprint.
- No PFX or private-key artifact is published.

## Container Apps import

After Key Vault import, the pipeline imports the certificate into the Container Apps environment with the versionless Key Vault secret URL and the environment system-assigned identity.

Container Apps can automatically apply a newer Key Vault certificate version after rotation. Microsoft notes that applying a rotated version can take up to 12 hours.

## Custom-domain cutover remains separate

This module intentionally does not:

- Add `craves.in` or `www.craves.in` as Container App hostnames.
- Bind the certificate to either hostname.
- Replace the current apex A records.
- Replace the current `www` CNAME.
- Remove the old Static Web App or its custom domains.

After certificate issuance, add and bind both custom domains, validate them against the Container App, then change DNS only after a successful pre-cutover check.

## Local validation

```bash
bash -n infra/certificates/web-letsencrypt/renew-web-certificate.sh
shellcheck infra/certificates/web-letsencrypt/renew-web-certificate.sh
```

## Manual intervention required

- Create the Azure DevOps pipeline from the YAML file.
- Confirm the pipeline variable `AZURE_SERVICE_CONNECTION` resolves to `Craves-Dev-Service-Connection`.
- Run the initial issuance with the confirmation parameter enabled.
- Complete the later custom-domain binding and DNS cutover steps.
- Keep all three ACME NS delegations permanently unless the related certificate automation is retired.

## Rollback

Certificate issuance and import do not move customer traffic. If this pipeline fails, keep the existing `@` and `www` records unchanged and investigate without deleting the current customer website or API records.

After the future customer-domain cutover, rollback consists of restoring the previous apex A records and `www` CNAME while retaining the new Key Vault certificate and ACME delegation for investigation.

## Operational risks

- Let's Encrypt rate limits apply. Do not repeatedly enable `forceIssue`.
- The Container Apps Key Vault certificate import command currently uses preview Key Vault-reference parameters in the Azure CLI extension.
- Container Apps certificate rotation is not instantaneous; retain a 30-day renewal window.
- Key Vault public access is currently enabled. Treat network hardening as a separate controlled change so certificate retrieval is not accidentally blocked.
