# Admin Chef Review and Secure Proof Streaming — Handover

Date: 2026-07-30

## Scope
Adds an operationally complete ADMIN chef-application review flow, including secure KYC proof viewing.

## Branch
`feature/admin-chef-review`.

## Dependency
Admin shell and existing User-Chef application/decision services.

## Backend gap closed
The previous backoffice application response contained proof metadata but no authenticated content endpoint. Approving without viewing evidence was unsafe.

## New backend endpoint

```text
GET /api/v1/backoffice/chef-reviews/{applicationId}/documents/{documentId}/content
```

## Backend authorization
`ChefDocumentReviewService` requires ADMIN role.

## Ownership validation
The document query requires both document ID and application ID.

## Storage boundary
Only the configured private documents container and `kyc/` blob prefix are allowed.

## MIME types
PDF, JPEG and PNG only.

## Size
The configured KYC upload limit is rechecked during download.

## URL policy
No SAS URL, connection string, container or blob name is returned to the browser.

## HTTP response
Inline content disposition, no-store cache control and exact content type/length.

## Next.js routes

```text
/admin/chef-reviews
/admin/chef-reviews/{applicationId}
/api/admin/chef-reviews/**
```

## Review list
Filters PENDING, APPROVED and REJECTED applications.

## Review details
Shows applicant contact/address, submitted/reviewed state and safe document metadata.

## Proof viewing
Each proof opens through the same-origin BFF. The BFF validates type/length, adds CSP sandbox and `nosniff`.

## Approval
Only PENDING applications show approval. Browser confirmation states that proof inspection is required.

## Rejection
Only PENDING applications show rejection. A non-empty reason up to 1000 characters is mandatory.

## Backend authority
User-Chef Service remains responsible for ADMIN role, pending-state enforcement, CHEF role grant, notifications and `admin_chef_decision_audit`.

## Privacy
Application identity, reviewer identity, blob container and blob name are removed by the browser contract.

## Origin protection
Approve and reject BFF routes require same-origin requests.

## Caching
All review JSON and binary proof responses are no-store.

## Tests
Frontend contract tests reject unsupported content types and prove private fields are stripped.

## CI
`azure-pipelines-admin-chef-review-ci.yml` builds User-Chef Service and Next.js, then applies security gates.

## APIM
Backoffice operations and proof content are configured in the consolidated APIM module later in the stack.

## Azure cost
No Azure resource was created. The existing private Blob container is reused only when deployed.

## Secrets
No storage credential, SAS token or Key Vault value was committed.

## Manual later
Run CI, deploy User-Chef Service, configure APIM, test PDF/image proofs, test non-admin denial, then perform controlled approve/reject on synthetic applications.

## Rollback
Restore the previous service/web images. No database migration or data deletion is required.

## Acceptance
ADMIN can inspect each proof and decide; non-admin receives denial; mismatched document/application IDs return not found; private storage identifiers never appear in browser responses.
