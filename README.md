# Craves Platform

Production-oriented platform repository for Craves: Azure infrastructure, APIM deployment support, internal dashboard tooling, and application code used during the Hyderabad MVP build.

## Approved backend architecture

The approved Craves backend architecture is **Java 21 + Spring Boot 3 + Maven**, deployed as independently deployable containerized services on **Azure Container Apps** and exposed through **Azure API Management**.

Do **not** build new Craves backend functionality in Node.js.

The target backend services are:

```text
Authentication Service
User and Chef Service
Catalog Service
Order Service
Subscription Service
Integration Service
Notification Service
```

## Important legacy note

The old `apps/api` folder was an earlier Node.js TypeScript prototype/reference. It is **not** the approved backend implementation path and must not be extended for the current Craves backend rewrite.

For current backend work, use Java 21 / Spring Boot service modules only. If a module is missing from this repository, create it as a Spring Boot service instead of adding to the legacy Node.js API.

## Current repository areas

```text
apps/api-test-dashboard       Internal browser dashboard for APIM/API validation only
apps/customer-web             Existing/legacy customer web app area; final web stack is Next.js + TypeScript + Tailwind
apps/admin-portal             Existing/legacy admin portal area; final web stack is Next.js + TypeScript + Tailwind
infra                         Azure infrastructure and deployment support
pipelines                     Azure DevOps YAML pipelines
shared                        Shared contracts/constants where applicable
docs                          Architecture and deployment notes
```

## Locked stack for active development

```text
Backend: Java 21, Spring Boot 3, Maven, PostgreSQL/PostGIS, Redis
Web: Next.js, TypeScript, Tailwind CSS
Mobile: React Native, TypeScript
Auth: Firebase Authentication with Craves JWT exchange
Payments: Cashfree
Delivery: Shiprocket adapter model
Cloud: Microsoft Azure, Azure Container Apps, APIM, ACR, Key Vault
CI/CD: Azure DevOps/GitHub Actions as explicitly selected per module
```

## Local development rule

Before starting or modifying a backend module, confirm the relevant HLD/FSD section and implement the module in Spring Boot. The Node.js prototype is read-only reference only.

Never commit real secrets. Use `.env` locally and Azure Key Vault / Azure DevOps variable groups in deployment.
