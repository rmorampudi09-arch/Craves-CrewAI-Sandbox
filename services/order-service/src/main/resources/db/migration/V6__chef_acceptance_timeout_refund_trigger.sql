ALTER TABLE order_schema.customer_order
    ADD COLUMN IF NOT EXISTS chef_acceptance_requested_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS chef_acceptance_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS chef_acceptance_initial_recorded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS chef_acceptance_reminder_10_recorded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS chef_acceptance_reminder_20_recorded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS chef_rejection_code VARCHAR(48),
    ADD COLUMN IF NOT EXISTS refund_requested_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS refund_requested_amount NUMERIC(10,2);

UPDATE order_schema.customer_order
SET chef_acceptance_requested_at = COALESCE(chef_acceptance_requested_at, now()),
    chef_acceptance_expires_at = COALESCE(chef_acceptance_expires_at, now() + INTERVAL '30 minutes'),
    updated_at = now()
WHERE status = 'CHEF_ACCEPTANCE_PENDING'
  AND (chef_acceptance_requested_at IS NULL OR chef_acceptance_expires_at IS NULL);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_customer_order_acceptance_window'
          AND conrelid = 'order_schema.customer_order'::regclass
    ) THEN
        ALTER TABLE order_schema.customer_order
            ADD CONSTRAINT chk_customer_order_acceptance_window
            CHECK (
                status <> 'CHEF_ACCEPTANCE_PENDING'
                OR (
                    chef_acceptance_requested_at IS NOT NULL
                    AND chef_acceptance_expires_at IS NOT NULL
                    AND chef_acceptance_expires_at > chef_acceptance_requested_at
                )
            )
            NOT VALID;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_customer_order_rejection_code'
          AND conrelid = 'order_schema.customer_order'::regclass
    ) THEN
        ALTER TABLE order_schema.customer_order
            ADD CONSTRAINT chk_customer_order_rejection_code
            CHECK (
                chef_rejection_code IS NULL
                OR chef_rejection_code IN ('CHEF_DECLINED', 'CHEF_ACCEPTANCE_TIMEOUT')
            )
            NOT VALID;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_customer_order_refund_request'
          AND conrelid = 'order_schema.customer_order'::regclass
    ) THEN
        ALTER TABLE order_schema.customer_order
            ADD CONSTRAINT chk_customer_order_refund_request
            CHECK (
                refund_requested_at IS NULL
                OR (
                    status = 'CHEF_REJECTED'
                    AND chef_rejection_code IS NOT NULL
                    AND refund_requested_amount IS NOT NULL
                    AND refund_requested_amount > 0
                )
            )
            NOT VALID;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_customer_order_acceptance_expiry
    ON order_schema.customer_order (chef_acceptance_expires_at, id)
    WHERE status = 'CHEF_ACCEPTANCE_PENDING';

CREATE INDEX IF NOT EXISTS idx_customer_order_acceptance_initial
    ON order_schema.customer_order (chef_acceptance_requested_at, id)
    WHERE status = 'CHEF_ACCEPTANCE_PENDING'
      AND chef_acceptance_initial_recorded_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_customer_order_acceptance_reminder_10
    ON order_schema.customer_order (chef_acceptance_requested_at, id)
    WHERE status = 'CHEF_ACCEPTANCE_PENDING'
      AND chef_acceptance_reminder_10_recorded_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_customer_order_acceptance_reminder_20
    ON order_schema.customer_order (chef_acceptance_requested_at, id)
    WHERE status = 'CHEF_ACCEPTANCE_PENDING'
      AND chef_acceptance_reminder_20_recorded_at IS NULL;

COMMENT ON COLUMN order_schema.customer_order.chef_acceptance_requested_at IS
    'UTC timestamp at which verified payment moved this chef-specific order into CHEF_ACCEPTANCE_PENDING.';

COMMENT ON COLUMN order_schema.customer_order.chef_acceptance_expires_at IS
    'UTC deadline for chef acceptance. V1 policy is 30 minutes after payment confirmation.';

COMMENT ON COLUMN order_schema.customer_order.chef_rejection_code IS
    'Machine-readable rejection reason: CHEF_DECLINED or CHEF_ACCEPTANCE_TIMEOUT.';

COMMENT ON COLUMN order_schema.customer_order.refund_requested_at IS
    'UTC timestamp at which Order Service transactionally created REFUND_REQUESTED for this chef-specific order.';

COMMENT ON COLUMN order_schema.customer_order.refund_requested_amount IS
    'Immutable requested refund amount copied from the chef-specific order grand_total.';
