CREATE TABLE payment_schema.chef_earning_entry (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    chef_identity_id UUID NOT NULL,
    order_source VARCHAR(30) NOT NULL,
    currency CHAR(3) NOT NULL,
    gross_amount NUMERIC(12,2) NOT NULL CHECK (gross_amount >= 0),
    commission_amount NUMERIC(12,2) NOT NULL CHECK (commission_amount >= 0),
    tax_withheld_amount NUMERIC(12,2) NOT NULL CHECK (tax_withheld_amount >= 0),
    adjustment_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    net_payable NUMERIC(12,2) NOT NULL CHECK (net_payable >= 0),
    allocation_reference VARCHAR(160) NOT NULL UNIQUE,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    reason VARCHAR(1000) NOT NULL,
    created_by_identity_id UUID NOT NULL,
    approved_by_identity_id UUID,
    approved_at TIMESTAMPTZ,
    reversed_by_identity_id UUID,
    reversed_at TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chef_earning_order_source CHECK (order_source IN ('ON_DEMAND', 'SUBSCRIPTION')),
    CONSTRAINT ck_chef_earning_status CHECK (
        status IN ('DRAFT', 'APPROVED', 'SETTLEMENT_PENDING', 'SETTLED', 'REVERSED')
    ),
    CONSTRAINT ck_chef_earning_arithmetic CHECK (
        net_payable = gross_amount - commission_amount - tax_withheld_amount + adjustment_amount
    )
);

CREATE INDEX ix_chef_earning_chef_status
    ON payment_schema.chef_earning_entry (chef_identity_id, status, created_at DESC);

CREATE TABLE payment_schema.chef_earning_audit (
    id UUID PRIMARY KEY,
    earning_entry_id UUID NOT NULL REFERENCES payment_schema.chef_earning_entry(id),
    action VARCHAR(40) NOT NULL,
    old_status VARCHAR(40),
    new_status VARCHAR(40),
    actor_identity_id UUID NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_chef_earning_audit_entry
    ON payment_schema.chef_earning_audit (earning_entry_id, created_at DESC);

CREATE TABLE payment_schema.chef_settlement_batch (
    id UUID PRIMARY KEY,
    batch_reference VARCHAR(160) NOT NULL UNIQUE,
    currency CHAR(3) NOT NULL,
    total_amount NUMERIC(14,2) NOT NULL CHECK (total_amount >= 0),
    entry_count INTEGER NOT NULL CHECK (entry_count > 0),
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    external_reference VARCHAR(200),
    failure_reason VARCHAR(1000),
    created_by_identity_id UUID NOT NULL,
    submitted_by_identity_id UUID,
    completed_by_identity_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_chef_settlement_status CHECK (
        status IN ('DRAFT', 'SUBMITTED', 'SETTLED', 'FAILED', 'CANCELLED')
    )
);

CREATE TABLE payment_schema.chef_settlement_item (
    batch_id UUID NOT NULL REFERENCES payment_schema.chef_settlement_batch(id),
    earning_entry_id UUID NOT NULL UNIQUE REFERENCES payment_schema.chef_earning_entry(id),
    chef_identity_id UUID NOT NULL,
    amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (batch_id, earning_entry_id)
);

CREATE INDEX ix_chef_settlement_item_chef
    ON payment_schema.chef_settlement_item (chef_identity_id, created_at DESC);

CREATE TABLE payment_schema.chef_settlement_audit (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES payment_schema.chef_settlement_batch(id),
    action VARCHAR(40) NOT NULL,
    old_status VARCHAR(40),
    new_status VARCHAR(40),
    actor_identity_id UUID NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    external_reference VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
