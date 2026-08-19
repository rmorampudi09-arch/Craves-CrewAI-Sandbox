ALTER TABLE payment_schema.payment_order
    ADD COLUMN IF NOT EXISTS customer_identity_id UUID;

UPDATE payment_schema.payment_order po
SET customer_identity_id = checkout.customer_identity_id
FROM (
    SELECT id,
           customer_identity_id
    FROM payment_schema.payment_order
    WHERE customer_identity_id IS NULL
) checkout
WHERE po.id = checkout.id;

ALTER TABLE payment_schema.payment_order
    ALTER COLUMN customer_identity_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_order_customer_identity_created_at
    ON payment_schema.payment_order (customer_identity_id, created_at DESC);
