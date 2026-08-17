# Chef financial ledger and settlement recording

This module provides an auditable, manual-first financial ledger for launch operations.

## Why manual-first

Craves commission, tax withholding, subscription allocation, refund allocation, settlement timing and payout-provider rules are product/legal/finance decisions. Source code therefore does not calculate them automatically.

An ADMIN submits the exact approved values for one Order. PostgreSQL independently enforces:

```text
net_payable = gross_amount - commission_amount - tax_withheld_amount + adjustment_amount
```

## Workflow

```text
ADMIN creates DRAFT allocation with reason
→ ADMIN reviews and approves
→ ADMIN groups approved entries into a settlement batch
→ external bank/payout operation is performed outside this module
→ ADMIN records external reference and SUBMITTED
→ ADMIN records SETTLED or FAILED
→ chef reads only own ledger entries
```

No API in this module sends money.

## APIs

```text
POST /api/v1/admin/chef-earnings
GET  /api/v1/admin/chef-earnings
POST /api/v1/admin/chef-earnings/{entryId}/approve
POST /api/v1/admin/chef-earnings/{entryId}/reverse
GET  /api/v1/chef/earnings
POST /api/v1/admin/chef-settlements
GET  /api/v1/admin/chef-settlements
PATCH /api/v1/admin/chef-settlements/{batchId}/status
```

Admin and chef paths use Craves JWT role enforcement inside Integration Service. Existing public payment/webhook/internal routes keep their previous contracts.

## Audit and idempotency

- `order_id` and `allocation_reference` are unique.
- Every allocation action stores an immutable JSON snapshot.
- Every settlement transition stores actor, reason and external reference.
- One earning entry can belong to only one settlement batch.
- Settlement transitions are explicit and row-locked.
- Failed/cancelled draft/submitted batches return entries to `APPROVED`.

## Subscription orders

Subscription Orders remain `financial_allocation_status=PENDING_POLICY`. An administrator can create an allocation only after finance has approved how the paid subscription invoice should be divided.

## Local test

```bash
cd services/integration-service
mvn -B -ntp verify
```

## Manual steps later

1. Apply Flyway V105.
2. Bind `CRAVES_JWT_VERIFICATION_PEM_BASE64` from Key Vault/secret reference.
3. Expose only the named APIs through guarded APIM operations.
4. Validate ADMIN/CHEF ownership and audit rows.
5. Establish the finance-approved allocation SOP.
6. Select and separately integrate a payout provider only after commercial approval.
