CREATE TABLE subscription_schema.chef_capacity_rule (
    id UUID PRIMARY KEY,
    chef_identity_id UUID NOT NULL,
    iso_day_of_week SMALLINT NOT NULL CHECK (iso_day_of_week BETWEEN 1 AND 7),
    meal_slot_code VARCHAR(40) NOT NULL,
    total_capacity_units INTEGER NOT NULL CHECK (total_capacity_units >= 0),
    subscription_capacity_units INTEGER NOT NULL CHECK (subscription_capacity_units >= 0),
    sales_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    updated_by_identity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chef_capacity_rule_slot CHECK (meal_slot_code ~ '^[A-Z0-9][A-Z0-9_-]{0,39}$'),
    CONSTRAINT ck_chef_capacity_rule_subscription_le_total CHECK (subscription_capacity_units <= total_capacity_units),
    CONSTRAINT ux_chef_capacity_rule UNIQUE (chef_identity_id, iso_day_of_week, meal_slot_code)
);

CREATE INDEX ix_chef_capacity_rule_chef_day
    ON subscription_schema.chef_capacity_rule (chef_identity_id, iso_day_of_week, meal_slot_code);

CREATE TABLE subscription_schema.chef_menu_item_capacity_rule (
    id UUID PRIMARY KEY,
    chef_identity_id UUID NOT NULL,
    menu_item_id UUID NOT NULL,
    iso_day_of_week SMALLINT NOT NULL CHECK (iso_day_of_week BETWEEN 1 AND 7),
    meal_slot_code VARCHAR(40) NOT NULL,
    max_subscription_units INTEGER NOT NULL CHECK (max_subscription_units >= 0),
    sales_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    updated_by_identity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chef_menu_capacity_rule_slot CHECK (meal_slot_code ~ '^[A-Z0-9][A-Z0-9_-]{0,39}$'),
    CONSTRAINT ux_chef_menu_capacity_rule UNIQUE (chef_identity_id, menu_item_id, iso_day_of_week, meal_slot_code)
);

CREATE INDEX ix_chef_menu_capacity_rule_lookup
    ON subscription_schema.chef_menu_item_capacity_rule (chef_identity_id, iso_day_of_week, meal_slot_code, menu_item_id);

CREATE TABLE subscription_schema.chef_capacity_override (
    id UUID PRIMARY KEY,
    chef_identity_id UUID NOT NULL,
    service_date DATE NOT NULL,
    meal_slot_code VARCHAR(40) NOT NULL,
    total_capacity_units INTEGER NOT NULL CHECK (total_capacity_units >= 0),
    subscription_capacity_units INTEGER NOT NULL CHECK (subscription_capacity_units >= 0),
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(1000),
    updated_by_identity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chef_capacity_override_slot CHECK (meal_slot_code ~ '^[A-Z0-9][A-Z0-9_-]{0,39}$'),
    CONSTRAINT ck_chef_capacity_override_subscription_le_total CHECK (subscription_capacity_units <= total_capacity_units),
    CONSTRAINT ux_chef_capacity_override UNIQUE (chef_identity_id, service_date, meal_slot_code)
);

CREATE INDEX ix_chef_capacity_override_chef_date
    ON subscription_schema.chef_capacity_override (chef_identity_id, service_date, meal_slot_code);

CREATE TABLE subscription_schema.chef_menu_item_capacity_override (
    id UUID PRIMARY KEY,
    chef_identity_id UUID NOT NULL,
    menu_item_id UUID NOT NULL,
    service_date DATE NOT NULL,
    meal_slot_code VARCHAR(40) NOT NULL,
    max_subscription_units INTEGER NOT NULL CHECK (max_subscription_units >= 0),
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    reason VARCHAR(1000),
    updated_by_identity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chef_menu_capacity_override_slot CHECK (meal_slot_code ~ '^[A-Z0-9][A-Z0-9_-]{0,39}$'),
    CONSTRAINT ux_chef_menu_capacity_override UNIQUE (chef_identity_id, menu_item_id, service_date, meal_slot_code)
);

CREATE INDEX ix_chef_menu_capacity_override_lookup
    ON subscription_schema.chef_menu_item_capacity_override (chef_identity_id, service_date, meal_slot_code, menu_item_id);

CREATE TABLE subscription_schema.chef_capacity_control (
    chef_identity_id UUID PRIMARY KEY,
    admin_sales_frozen BOOLEAN NOT NULL DEFAULT FALSE,
    freeze_reason VARCHAR(1000),
    frozen_by_identity_id UUID,
    frozen_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chef_capacity_control_freeze CHECK (
        (admin_sales_frozen = TRUE AND freeze_reason IS NOT NULL AND frozen_by_identity_id IS NOT NULL AND frozen_at IS NOT NULL)
        OR admin_sales_frozen = FALSE
    )
);

CREATE TABLE subscription_schema.subscription_capacity_entitlement (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES subscription_schema.customer_subscription(id) ON DELETE CASCADE,
    chef_identity_id UUID NOT NULL,
    recurrence_type VARCHAR(20) NOT NULL,
    iso_day_of_week SMALLINT,
    day_of_month SMALLINT,
    meal_slot_code VARCHAR(40) NOT NULL,
    menu_item_id UUID NOT NULL,
    units INTEGER NOT NULL CHECK (units BETWEEN 1 AND 10000),
    status VARCHAR(20) NOT NULL,
    hold_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_subscription_capacity_entitlement_recurrence CHECK (
        (recurrence_type = 'WEEKLY' AND iso_day_of_week BETWEEN 1 AND 7 AND day_of_month IS NULL)
        OR (recurrence_type = 'MONTHLY' AND day_of_month BETWEEN 1 AND 28 AND iso_day_of_week IS NULL)
    ),
    CONSTRAINT ck_subscription_capacity_entitlement_slot CHECK (meal_slot_code ~ '^[A-Z0-9][A-Z0-9_-]{0,39}$'),
    CONSTRAINT ck_subscription_capacity_entitlement_status CHECK (status IN ('HOLD', 'COMMITTED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_subscription_capacity_entitlement_expiry CHECK (
        (status = 'HOLD' AND hold_expires_at IS NOT NULL) OR status <> 'HOLD'
    )
);

CREATE UNIQUE INDEX ux_subscription_capacity_entitlement_item
    ON subscription_schema.subscription_capacity_entitlement (
        subscription_id,
        recurrence_type,
        COALESCE(iso_day_of_week, 0),
        COALESCE(day_of_month, 0),
        meal_slot_code,
        menu_item_id
    )
    WHERE status IN ('HOLD', 'COMMITTED');

CREATE INDEX ix_capacity_entitlement_slot_active
    ON subscription_schema.subscription_capacity_entitlement (
        chef_identity_id, meal_slot_code, recurrence_type, iso_day_of_week, day_of_month, status
    )
    WHERE status IN ('HOLD', 'COMMITTED');

CREATE INDEX ix_capacity_entitlement_hold_expiry
    ON subscription_schema.subscription_capacity_entitlement (hold_expires_at)
    WHERE status = 'HOLD';

CREATE TABLE subscription_schema.chef_capacity_bucket (
    chef_identity_id UUID NOT NULL,
    service_date DATE NOT NULL,
    meal_slot_code VARCHAR(40) NOT NULL,
    total_capacity_units INTEGER NOT NULL CHECK (total_capacity_units >= 0),
    subscription_capacity_units INTEGER NOT NULL CHECK (subscription_capacity_units >= 0),
    held_units INTEGER NOT NULL DEFAULT 0 CHECK (held_units >= 0),
    committed_units INTEGER NOT NULL DEFAULT 0 CHECK (committed_units >= 0),
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    deficit_units INTEGER NOT NULL DEFAULT 0 CHECK (deficit_units >= 0),
    source VARCHAR(20) NOT NULL,
    config_version INTEGER NOT NULL DEFAULT 1 CHECK (config_version > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (chef_identity_id, service_date, meal_slot_code),
    CONSTRAINT ck_chef_capacity_bucket_slot CHECK (meal_slot_code ~ '^[A-Z0-9][A-Z0-9_-]{0,39}$'),
    CONSTRAINT ck_chef_capacity_bucket_subscription_le_total CHECK (subscription_capacity_units <= total_capacity_units),
    CONSTRAINT ck_chef_capacity_bucket_source CHECK (source IN ('RULE', 'OVERRIDE'))
);

CREATE INDEX ix_chef_capacity_bucket_deficit
    ON subscription_schema.chef_capacity_bucket (service_date, deficit_units DESC)
    WHERE deficit_units > 0;

CREATE TABLE subscription_schema.chef_menu_item_capacity_bucket (
    chef_identity_id UUID NOT NULL,
    menu_item_id UUID NOT NULL,
    service_date DATE NOT NULL,
    meal_slot_code VARCHAR(40) NOT NULL,
    max_subscription_units INTEGER NOT NULL CHECK (max_subscription_units >= 0),
    held_units INTEGER NOT NULL DEFAULT 0 CHECK (held_units >= 0),
    committed_units INTEGER NOT NULL DEFAULT 0 CHECK (committed_units >= 0),
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    deficit_units INTEGER NOT NULL DEFAULT 0 CHECK (deficit_units >= 0),
    source VARCHAR(20) NOT NULL,
    config_version INTEGER NOT NULL DEFAULT 1 CHECK (config_version > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (chef_identity_id, menu_item_id, service_date, meal_slot_code),
    CONSTRAINT ck_chef_menu_capacity_bucket_slot CHECK (meal_slot_code ~ '^[A-Z0-9][A-Z0-9_-]{0,39}$'),
    CONSTRAINT ck_chef_menu_capacity_bucket_source CHECK (source IN ('RULE', 'OVERRIDE'))
);

CREATE INDEX ix_chef_menu_capacity_bucket_deficit
    ON subscription_schema.chef_menu_item_capacity_bucket (service_date, deficit_units DESC)
    WHERE deficit_units > 0;

CREATE TABLE subscription_schema.subscription_capacity_allocation (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES subscription_schema.customer_subscription(id) ON DELETE CASCADE,
    chef_identity_id UUID NOT NULL,
    service_date DATE NOT NULL,
    meal_slot_code VARCHAR(40) NOT NULL,
    menu_item_id UUID NOT NULL,
    units INTEGER NOT NULL CHECK (units BETWEEN 1 AND 10000),
    status VARCHAR(20) NOT NULL,
    hold_expires_at TIMESTAMPTZ,
    occurrence_id UUID REFERENCES subscription_schema.subscription_occurrence(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_subscription_capacity_allocation_slot CHECK (meal_slot_code ~ '^[A-Z0-9][A-Z0-9_-]{0,39}$'),
    CONSTRAINT ck_subscription_capacity_allocation_status CHECK (status IN ('HOLD', 'COMMITTED', 'MATERIALIZED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT ck_subscription_capacity_allocation_expiry CHECK (
        (status = 'HOLD' AND hold_expires_at IS NOT NULL) OR status <> 'HOLD'
    ),
    CONSTRAINT ux_subscription_capacity_allocation UNIQUE (subscription_id, service_date, meal_slot_code, menu_item_id)
);

CREATE INDEX ix_capacity_allocation_bucket_active
    ON subscription_schema.subscription_capacity_allocation (
        chef_identity_id, service_date, meal_slot_code, status
    )
    WHERE status IN ('HOLD', 'COMMITTED', 'MATERIALIZED');

CREATE INDEX ix_capacity_allocation_item_active
    ON subscription_schema.subscription_capacity_allocation (
        chef_identity_id, menu_item_id, service_date, meal_slot_code, status
    )
    WHERE status IN ('HOLD', 'COMMITTED', 'MATERIALIZED');

CREATE INDEX ix_capacity_allocation_subscription
    ON subscription_schema.subscription_capacity_allocation (subscription_id, service_date, meal_slot_code);

CREATE TABLE subscription_schema.capacity_incident (
    id UUID PRIMARY KEY,
    chef_identity_id UUID NOT NULL,
    service_date DATE,
    iso_day_of_week SMALLINT CHECK (iso_day_of_week IS NULL OR iso_day_of_week BETWEEN 1 AND 7),
    meal_slot_code VARCHAR(40) NOT NULL,
    menu_item_id UUID,
    incident_type VARCHAR(40) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    reserved_units INTEGER NOT NULL CHECK (reserved_units >= 0),
    capacity_units INTEGER NOT NULL CHECK (capacity_units >= 0),
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT ck_capacity_incident_slot CHECK (meal_slot_code ~ '^[A-Z0-9][A-Z0-9_-]{0,39}$'),
    CONSTRAINT ck_capacity_incident_type CHECK (incident_type IN ('RECURRING_DEFICIT', 'DATE_DEFICIT', 'ITEM_DEFICIT', 'PROJECTION_FAILURE')),
    CONSTRAINT ck_capacity_incident_severity CHECK (severity IN ('P1', 'P2', 'P3', 'P4')),
    CONSTRAINT ck_capacity_incident_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_capacity_incident_resolved CHECK ((status = 'RESOLVED' AND resolved_at IS NOT NULL) OR status = 'OPEN')
);

CREATE INDEX ix_capacity_incident_open
    ON subscription_schema.capacity_incident (chef_identity_id, created_at DESC)
    WHERE status = 'OPEN';

CREATE UNIQUE INDEX ux_capacity_incident_open_scope
    ON subscription_schema.capacity_incident (
        chef_identity_id,
        COALESCE(service_date, DATE '0001-01-01'),
        COALESCE(iso_day_of_week, 0),
        meal_slot_code,
        COALESCE(menu_item_id, '00000000-0000-0000-0000-000000000000'::UUID),
        incident_type
    )
    WHERE status = 'OPEN';

CREATE TABLE subscription_schema.capacity_audit (
    id UUID PRIMARY KEY,
    chef_identity_id UUID NOT NULL,
    actor_identity_id UUID NOT NULL,
    action VARCHAR(60) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_key VARCHAR(300) NOT NULL,
    reason VARCHAR(1000),
    old_state JSONB,
    new_state JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_capacity_audit_chef_created
    ON subscription_schema.capacity_audit (chef_identity_id, created_at DESC);
