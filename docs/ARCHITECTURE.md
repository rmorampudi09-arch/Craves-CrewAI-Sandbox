# Craves Architecture

## Principle
Build production-grade code and deploy on small infrastructure first.

## Current starter deployment
- Customer Web: Static hosting later
- Admin Portal: Static hosting later
- API: Node TypeScript Fastify
- Database: PostgreSQL Flexible Server
- Secrets: Azure Key Vault
- Monitoring: Application Insights + Log Analytics
- Mobile: Flutter

## Auth direction
- Firebase Phone OTP validates phone ownership.
- Backend exchanges verified Firebase token for Craves JWT.
- Roles: Customer, Chef, Admin.
- Chef mode requires admin approval before marketplace listing.

## Scale-up path
- Add Redis when caching/session pressure appears.
- Add Front Door/WAF when public security/traffic increases.
- Add AKS only when service complexity justifies it.
- Add multi-region DR after revenue/business continuity requires it.
