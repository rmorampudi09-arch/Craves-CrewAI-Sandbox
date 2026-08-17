ALTER TABLE order_schema.customer_order
    DROP CONSTRAINT IF EXISTS chk_customer_order_refund_request;

ALTER TABLE order_schema.customer_order
    ADD CONSTRAINT chk_customer_order_refund_request
    CHECK (
        refund_requested_at IS NULL
        OR (
            status IN (
                'CHEF_REJECTED',
                'REFUND_PENDING',
                'REFUNDED',
                'REFUND_FAILED'
            )
            AND chef_rejection_code IS NOT NULL
            AND refund_requested_amount IS NOT NULL
            AND refund_requested_amount > 0
        )
    )
    NOT VALID;

COMMENT ON CONSTRAINT chk_customer_order_refund_request
    ON order_schema.customer_order IS
    'Requires rejection and a positive refund request amount throughout the complete refund lifecycle.';
