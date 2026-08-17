# Delivery Intelligence Spring Constructor Incident and Fix — 14 July 2026

## Deployment state observed

The Integration Service pipeline produced image tag `66` and Azure Container Apps created revision:

```text
ca-craves-integration-service-pr--0000004
```

Flyway was healthy on this redeployment:

- PostgreSQL 16.14 connection succeeded.
- Three migrations validated.
- `payment_schema` was at version 2.
- No database migration was required.

The new revision nevertheless failed during Spring application-context creation. The previous ready revision remained:

```text
ca-craves-integration-service-pr--0000002
```

## Root cause

`DeliveryIntelligenceService` had two constructors:

1. The production constructor using `Clock.systemUTC()`.
2. A package-private constructor accepting an explicit `Clock` for deterministic tests.

Because multiple constructors existed and neither was explicitly selected for dependency injection, Spring attempted default-constructor instantiation and failed with:

```text
No default constructor found
java.lang.NoSuchMethodException: DeliveryIntelligenceService.<init>()
```

`DeliveryMetricsMaintenanceService` used the same constructor pattern and was corrected proactively before it could become the next startup failure.

## Code changes

The production constructors are now explicitly marked with Spring `@Autowired` while the package-private clock-aware constructors remain available for deterministic unit testing.

Files:

```text
services/integration-service/src/main/java/in/craves/integration/delivery/DeliveryIntelligenceService.java
services/integration-service/src/main/java/in/craves/integration/delivery/DeliveryMetricsMaintenanceService.java
```

Commits:

```text
2bf16af0caa835b325323b1824844126037c6698
b7707f0495503491e9467af4e2c86d77fc4b002f
```

## Regression coverage

A focused Spring application-context wiring test was added. It registers mocked dependencies, asks Spring to instantiate both delivery services, and fails the Maven build if constructor selection breaks again.

File:

```text
services/integration-service/src/test/java/in/craves/integration/delivery/DeliverySpringWiringTest.java
```

Commit:

```text
81646bcc125464805d609319261ff6d658547210
```

## Required next action

Rerun the existing Integration Service Azure DevOps pipeline from `main`. Confirm:

1. Maven compilation and tests pass, including `DeliverySpringWiringTest`.
2. A new Container Apps revision is created.
3. `latestRevisionName` equals `latestReadyRevisionName`.
4. Startup logs include `Started IntegrationServiceApplication` and no constructor error.
5. Health returns `UP` from the new ready revision.

## Do not change

- Do not alter Flyway history; version 2 is already successfully installed.
- Do not rerun migrations manually.
- Do not enable PostgreSQL extensions.
- Do not shift traffic manually while the new revision is not ready.
- Do not deactivate the previous healthy revision until the new revision is confirmed ready.
