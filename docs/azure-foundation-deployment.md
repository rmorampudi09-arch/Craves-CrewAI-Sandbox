# Craves Azure Foundation Deployment

This guide deploys the Craves full-app low-capacity production foundation to the existing Azure Startup subscription.

## Current safety status

Do **not** run `az deployment group create` until both conditions are true:

1. PostgreSQL Flexible Server provisioning is available in the chosen region.
2. `az deployment group validate` and `az deployment group what-if` are clean and expected.

The earlier South India deployment failed because PostgreSQL Flexible Server returned `LocationIsOfferRestricted`. The old `Microsoft.Cache/Redis` resource was also rejected because Azure Cache for Redis is retiring. The current template removes Redis from the first safe foundation deployment. Azure Managed Redis can be added later after provider, SKU, region, and cost are confirmed.

## Prerequisites

Repository secrets:

- `AZURE_CLIENT_ID`
- `AZURE_TENANT_ID`
- `AZURE_SUBSCRIPTION_ID`
- `POSTGRES_ADMIN_PASSWORD`

Repository variables:

- `AZURE_RESOURCE_GROUP`
- `AZURE_LOCATION`
- `ENV_NAME`
- `PROJECT`
- `ACR_NAME`

## Current target

- Tenant: `6ac26780-f774-41e0-bf31-45f5e6e7167d`
- Subscription: `Craves-Dev`
- Subscription ID: `4f897b61-9b52-44b4-8cf1-bdac281cc1aa`
- Primary requested region: `southindia`
- Practical fallback/default for current deployment: `centralindia`
- Resource group if using South India: `rg-craves-prodlow-southindia`
- Resource group if using Central India: `rg-craves-prodlow-centralindia`

## Deployment resources

The Bicep template provisions:

- Azure Container Registry
- Azure Container Apps Environment
- Eight Container Apps placeholders for web plus seven backend services
- PostgreSQL Flexible Server with `craves_auth_db`, `craves_business_db`, and `craves_integration_db`
- Azure Service Bus namespace, domain event topic, and command queues
- Azure Blob Storage containers for media and documents
- Azure Key Vault with RBAC enabled
- API Management Consumption
- Application Insights
- Log Analytics

The template does **not** provision Redis in the first foundation pass. This is intentional. Do not add old `Microsoft.Cache/Redis`; use Azure Managed Redis later only after validating availability and cost.

The template does **not** provision CDN/Azure Front Door in the first foundation pass. Chef media still lands in private Blob Storage containers. CDN/Azure Front Door should be added after the catalog/media upload module is built, because caching rules must be different for public media and authenticated APIs.

## Recommended GitHub Actions deployment path

Use `.github/workflows/azure-foundation-deploy.yml` from GitHub Actions.

Workflow inputs:

- `action`: `validate`, `what-if`, or `deploy`
- `azure_location`: `centralindia` or `southindia`
- `resource_group`: target resource group name

Recommended sequence:

1. Run `validate` with:
   - `azure_location = centralindia`
   - `resource_group = rg-craves-prodlow-centralindia`
2. If validation passes, run `what-if` with the same values.
3. Review the what-if output.
4. Run `deploy` only if the output is expected.

Do not run `deploy` directly as the first action.

## Safe pre-check commands

Run from Azure Cloud Shell after confirming the correct subscription is selected.

```bash
az account set --subscription 4f897b61-9b52-44b4-8cf1-bdac281cc1aa

az account show --query "{Name:name, SubscriptionId:id, TenantId:tenantId}" -o table

az group exists --name rg-craves-prodlow-southindia

az postgres flexible-server list-skus --location southindia -o table
az postgres flexible-server list-skus --location centralindia -o table | head -30
```

## South India path

Use this only after Microsoft confirms PostgreSQL Flexible Server provisioning access in South India and `az postgres flexible-server list-skus --location southindia -o table` shows the required SKU.

```bash
az group create \
  --name rg-craves-prodlow-southindia \
  --location southindia

az deployment group validate \
  --resource-group rg-craves-prodlow-southindia \
  --template-file infra/main.bicep \
  --parameters location=southindia \
               environmentName=prodlow \
               projectName=craves \
               acrName=cravesprodlowacr82121 \
               postgresAdminPassword='<use-rotated-password-from-secret-store>'

az deployment group what-if \
  --resource-group rg-craves-prodlow-southindia \
  --template-file infra/main.bicep \
  --parameters location=southindia \
               environmentName=prodlow \
               projectName=craves \
               acrName=cravesprodlowacr82121 \
               postgresAdminPassword='<use-rotated-password-from-secret-store>'
```

Only if validation and what-if are clean:

```bash
az deployment group create \
  --name craves-foundation-001 \
  --resource-group rg-craves-prodlow-southindia \
  --template-file infra/main.bicep \
  --parameters location=southindia \
               environmentName=prodlow \
               projectName=craves \
               acrName=cravesprodlowacr82121 \
               postgresAdminPassword='<use-rotated-password-from-secret-store>'
```

## Central India fallback path

Use this if South India is still blocked and you decide to launch the first low-capacity production foundation from Central India.

```bash
az group create \
  --name rg-craves-prodlow-centralindia \
  --location centralindia

az deployment group validate \
  --resource-group rg-craves-prodlow-centralindia \
  --template-file infra/main.bicep \
  --parameters location=centralindia \
               environmentName=prodlow \
               projectName=craves \
               acrName=cravesprodlowacr82121 \
               postgresAdminPassword='<use-rotated-password-from-secret-store>'

az deployment group what-if \
  --resource-group rg-craves-prodlow-centralindia \
  --template-file infra/main.bicep \
  --parameters location=centralindia \
               environmentName=prodlow \
               projectName=craves \
               acrName=cravesprodlowacr82121 \
               postgresAdminPassword='<use-rotated-password-from-secret-store>'
```

Only if validation and what-if are clean:

```bash
az deployment group create \
  --name craves-foundation-001 \
  --resource-group rg-craves-prodlow-centralindia \
  --template-file infra/main.bicep \
  --parameters location=centralindia \
               environmentName=prodlow \
               projectName=craves \
               acrName=cravesprodlowacr82121 \
               postgresAdminPassword='<use-rotated-password-from-secret-store>'
```

## Important secret rule

Never commit real secrets. Store provider credentials in Azure Key Vault and map them into Container Apps later. The PostgreSQL admin password used in the previous chat was exposed in chat and must be rotated before real production use.
