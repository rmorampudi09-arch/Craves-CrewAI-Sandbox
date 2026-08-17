ALTER TABLE order_schema.customer_order
    ADD COLUMN IF NOT EXISTS refund_provider VARCHAR(30),
    ADD COLUMN IF NOT EXISTS provider_refund_id VARCHAR(160);

UPDATE order_schema.customer_order
SET refund_provider = 'CASHFREE',
    provider_refund_id = cf_refund_id
WHERE cf_refund_id IS NOT NULL
  AND refund_provider IS NULL;
