# Craves Integration Refund Deployment Checkpoint

**Date:** 16 July 2026  
**Service:** Integration Service  
**Git baseline:** `1d07be8f96a6d222b3c0a2fe83c143567f27ed6c`  
**Deployment pipeline:** `azure-pipelines-integration-service.yml`  
**Reported result:** Successful

## Completed

- Integration refund workflow merged to `main`.
- Java 21/Maven CI passed after correcting the Azure Service Bus receive-mode import.
- Normal Integration Service deployment pipeline completed successfully.
- Flyway migration `V100__refund_workflow_foundation.sql` is included in the deployed image.
- Refund consumer, Cashfree provider execution, reconciliation and refund-status publication remain disabled by default.

## Required verification

Run:

```bash
cd ~/Craves-Build-platform
git pull origin main
chmod +x scripts/verify-integration-refund-deployment.sh
./scripts/verify-integration-refund-deployment.sh
```

The verification checks:

- correct Integration Service image;
- latest revision equals latest ready revision;
- Container App and revision are healthy;
- `/actuator/health` responds;
- all refund execution switches are false or absent;
- delivery command and Borzo outbound execution remain disabled;
- available Flyway V100 log evidence;
- whether the refund Service Bus subscription already exists.

## Next controlled step

After verification passes, run:

```text
azure-pipelines-integration-refund-consumer-enable.yml
Branch: main
```

That pipeline may create the existing-topic subscription `integration-service-refund-requested`, add the filter `eventType = 'REFUND_REQUESTED'`, grant the Integration managed identity `Azure Service Bus Data Receiver`, and enable only the refund consumer.

The following must remain false:

```text
CRAVES_REFUND_PROVIDER_EXECUTION_ENABLED=false
CRAVES_REFUND_RECONCILIATION_ENABLED=false
CRAVES_REFUND_STATUS_PUBLISHER_ENABLED=false
```

No Cashfree API call can occur in this stage.

## Still pending

- controlled invalid-message dead-letter test;
- Order Service consumer for `REFUND_STATUS_CHANGED`;
- customer refund-status notifications;
- controlled Cashfree sandbox execution;
- controlled Order timeout worker and `REFUND_REQUESTED` publication enablement;
- end-to-end paid-order rejection/refund test;
- Key Vault migration after all modules stabilize.
