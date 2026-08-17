# Backend production evidence checklist

Mark an item complete only when its linked evidence exists.

## Source and CI

- [ ] Final merged SHA recorded
- [ ] All module CI green
- [ ] Final production-readiness preflight green
- [ ] Java 21 verification green for all seven services
- [ ] Next.js typecheck, tests and build green
- [ ] No secret or privacy gate findings

## Merge

- [ ] Parent-first PR merge list recorded
- [ ] PR 93 ancestry handled correctly
- [ ] Protected-main checks enforced
- [ ] No unresolved review threads

## Deployment

- [ ] Seven image digests recorded
- [ ] Seven previous rollback revisions recorded
- [ ] Seven latest revisions ready and healthy
- [ ] All new flags disabled after initial deployment

## Database

- [ ] Backup/restore point recorded
- [ ] Flyway history verified per service
- [ ] Migration checksums recorded
- [ ] Service schema ownership verified

## Azure platform

- [ ] Managed identities least-privileged
- [ ] ACR pull assignments verified
- [ ] Key Vault/secret references verified without values
- [ ] Service Bus topics/subscriptions/filters verified
- [ ] APIM policies read back and stored
- [ ] Unauthenticated gateway smoke returns 401

## Dependencies

- [ ] Redis TLS and all seven bindings verified
- [ ] Firebase service-account permissions verified
- [ ] FCM controlled destination test completed
- [ ] ACS sender/domain test completed
- [ ] Cashfree webhook and signing validated
- [ ] Delivery provider credentials/callback/coverage validated

## Activation

- [ ] Pre-activation flag snapshot saved
- [ ] Consumers enabled before publishers
- [ ] Webhooks enabled before provider execution
- [ ] Account API enabled before Firebase worker
- [ ] Notification recovery enabled before provider delivery
- [ ] Rollback pipelines validated before activation

## Production validation

- [ ] Authorization matrix passed
- [ ] Security scans passed
- [ ] 50–100 concurrent-user load test passed
- [ ] Queue/provider failure tests passed
- [ ] Backup restore rehearsal passed
- [ ] Alerts and dashboards tested
- [ ] Controlled provider field tests signed off

## Go-live

- [ ] Product-owned policy values approved
- [ ] Billing/SKU review approved
- [ ] Backend go-live approval recorded
- [ ] CDN/Front Door work package authorized
