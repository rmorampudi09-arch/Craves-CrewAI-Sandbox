ALTER TABLE subscription_schema.capacity_incident
    DROP CONSTRAINT ck_capacity_incident_type;

ALTER TABLE subscription_schema.capacity_incident
    ADD CONSTRAINT ck_capacity_incident_type CHECK (
        incident_type IN (
            'RECURRING_DEFICIT',
            'DATE_DEFICIT',
            'ITEM_DEFICIT',
            'PROJECTION_FAILURE',
            'PAID_CAPACITY_CONFLICT'
        )
    );
