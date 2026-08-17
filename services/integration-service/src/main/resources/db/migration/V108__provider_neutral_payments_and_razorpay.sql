-- V108 follows the existing V105-V107 production migration sequence.
ALTER TABLE payment_schema.payment_order
    ADD COLUMN IF NOT EXISTS provider VARCHAR(30) NOT NULL DEFAULT 'CASHFREE',
    ADD COLUMN IF NOT EXISTS provider_order_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS provider_payment_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS checkout_key_id VARCHAR(160);

UPDATE payment_schema.payment_order
SET provider_order_id = cashfree_order_id,
    provider_payment_id = COALESCE(provider_payment_id, cashfree_cf_order_id)
WHERE provider = 'CASHFREE'
  AND provider_order_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_order_provider_identity
    ON payment_schema.payment_order (provider, provider_order_id)
    WHERE provider_order_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_payment_order_provider_payment
    ON payment_schema.payment_order (provider, provider_payment_id)
    WHERE provider_payment_id IS NOT NULL;

ALTER TABLE payment_schema.payment_attempt
    ADD COLUMN IF NOT EXISTS provider VARCHAR(30) NOT NULL DEFAULT 'CASHFREE',
    ADD COLUMN IF NOT EXISTS provider_payment_id VARCHAR(160);

UPDATE payment_schema.payment_attempt
SET provider_payment_id = cf_payment_id
WHERE provider = 'CASHFREE'
  AND provider_payment_id IS NULL;

ALTER TABLE payment_schema.subscription_payment_intent
    ADD COLUMN IF NOT EXISTS provider VARCHAR(30) NOT NULL DEFAULT 'CASHFREE',
    ADD COLUMN IF NOT EXISTS provider_order_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS provider_payment_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS checkout_key_id VARCHAR(160);

UPDATE payment_schema.subscription_payment_intent
SET provider_order_id = cashfree_order_id,
    provider_payment_id = COALESCE(provider_payment_id, cashfree_cf_order_id)
WHERE provider = 'CASHFREE'
  AND provider_order_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_subscription_payment_provider_identity
    ON payment_schema.subscription_payment_intent (provider, provider_order_id)
    WHERE provider_order_id IS NOT NULL;

ALTER TABLE payment_schema.refund
    ADD COLUMN IF NOT EXISTS provider VARCHAR(30) NOT NULL DEFAULT 'CASHFREE',
    ADD COLUMN IF NOT EXISTS provider_order_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS provider_payment_id VARCHAR(160),
    ADD COLUMN IF NOT EXISTS provider_refund_id VARCHAR(160);

UPDATE payment_schema.refund
SET provider_order_id = cashfree_order_id,
    provider_refund_id = cf_refund_id
WHERE provider = 'CASHFREE';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_payment_order_provider'
          AND conrelid = 'payment_schema.payment_order'::regclass
    ) THEN
        ALTER TABLE payment_schema.payment_order
            ADD CONSTRAINT ck_payment_order_provider CHECK (provider IN ('CASHFREE', 'RAZORPAY')) NOT VALID;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_subscription_payment_provider'
          AND conrelid = 'payment_schema.subscription_payment_intent'::regclass
    ) THEN
        ALTER TABLE payment_schema.subscription_payment_intent
            ADD CONSTRAINT ck_subscription_payment_provider CHECK (provider IN ('CASHFREE', 'RAZORPAY')) NOT VALID;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_refund_provider'
          AND conrelid = 'payment_schema.refund'::regclass
    ) THEN
        ALTER TABLE payment_schema.refund
            ADD CONSTRAINT ck_refund_provider CHECK (provider IN ('CASHFREE', 'RAZORPAY')) NOT VALID;
    END IF;
END
$$;

COMMENT ON COLUMN payment_schema.payment_order.provider IS
    'Selected payment provider. Runtime cutover is controlled by PAYMENT_PROVIDER_NAME.';
COMMENT ON COLUMN payment_schema.payment_order.checkout_key_id IS
    'Public checkout key identifier. Provider secrets are never stored in payment rows.';
