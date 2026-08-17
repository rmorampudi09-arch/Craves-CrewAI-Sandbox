# Subscription plan schedules

A subscription plan cannot generate recurring meals until its owner defines and activates an explicit schedule.

## API

```text
GET  /api/v1/admin/subscription-plans/{planId}/schedule
PUT  /api/v1/admin/subscription-plans/{planId}/schedule
POST /api/v1/admin/subscription-plans/{planId}/schedule/activate
```

ADMIN may manage any plan. CHEF may manage only a plan whose `chef_identity_id` matches the authenticated identity.

## Schedule contract

- recurrence must match the existing plan billing period;
- timezone must be a valid IANA timezone;
- service time and generation lead hours are mandatory;
- WEEKLY items require ISO day 1–7;
- MONTHLY items require day 1–28;
- every item requires a Catalog menu item, quantity and sequence;
- the menu item must be active, available and owned by the plan chef;
- schedule activation requires a reason and a non-empty item set.

Replacing an active schedule is blocked. Operations must first inactivate the plan/schedule through a later controlled lifecycle action rather than silently changing future meals.

## Database

Flyway V3 adds schedule, schedule-item and audit tables. Menu item IDs are service references; Catalog remains authoritative, so no cross-service foreign key is created.

## Local test

```bash
cd services/subscription-service
mvn -B -ntp verify
```

No default service days, meal times, menu items, quantities, pause rules or holiday rules are supplied by code.
