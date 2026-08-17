# CRV-AUTH-001 - Authentication and Authorization LLD

Version: 0.1
Status: Draft for Module 1 implementation
Owner: Craves Engineering

## Purpose

This document defines the first implementation slice of Craves authentication.

Firebase verifies phone possession. Craves Auth Service owns internal identity, roles, sessions and audit records.

## Confirmed decisions

- Primary login: phone OTP through Firebase.
- Customer email: optional.
- Chef email: mandatory during chef onboarding and must be verified.
- Roles: CUSTOMER, CHEF, ADMIN.
- Same account can be customer and chef.
- Chef mode is blocked until admin approval.
- Admin users will be created later through the admin portal.
- Auth Service verifies Firebase login result and issues Craves application session credentials.

## Initial API scope

- POST /api/v1/auth/firebase/exchange
- POST /api/v1/auth/refresh
- POST /api/v1/auth/logout
- GET /api/v1/auth/me

## Data ownership

Auth Service owns these craves_auth_db tables:

- auth_identity
- auth_role
- auth_identity_role
- refresh_session
- login_attempt
- auth_audit

Customer profile, chef profile, addresses, kitchen, documents, KYC, FSSAI and approval workflow belong to the User/Chef Service.

## Role policy

- New phone-verified users get CUSTOMER by default.
- CHEF is granted only after chef onboarding and approval in later modules.
- ADMIN is not seeded in this module.

## Future extensions

- Fast revocation and rate limiting.
- Admin portal role management.
- User/Chef onboarding service integration.
- Email verification enforcement for chef onboarding.
