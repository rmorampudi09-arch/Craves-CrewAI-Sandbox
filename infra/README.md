# Craves Azure Infrastructure

Starter low-cost deployment for 50-100 concurrent users:
- Resource group
- PostgreSQL Flexible Server Burstable
- Key Vault
- Storage Account
- Log Analytics
- Application Insights

Avoided initially:
- AKS
- Application Gateway
- Front Door Premium
- Redis
- NAT Gateway
- Multi-region DR

Deploy example:

```bash
az group create -n rg-craves-dev -l centralindia
az deployment group create -g rg-craves-dev -f infra/main.bicep -p environmentName=dev postgresAdminPassword='CHANGE_ME'
```
