CREATE INDEX IF NOT EXISTS idx_order_customer_order_created_at
    ON order_schema.customer_order (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_order_customer_order_exception_updated_at
    ON order_schema.customer_order (updated_at DESC)
    WHERE status IN ('CHEF_REJECTED', 'CANCELLED', 'REFUND_PENDING', 'REFUND_FAILED');

CREATE INDEX IF NOT EXISTS idx_order_customer_order_delivered_updated_at
    ON order_schema.customer_order (updated_at DESC)
    WHERE status = 'DELIVERED';
