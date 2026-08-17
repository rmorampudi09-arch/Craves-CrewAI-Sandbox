CREATE TABLE order_schema.checkout_pricing_quote (
    id UUID PRIMARY KEY,
    customer_identity_id UUID NOT NULL,
    delivery_address_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    cart_fingerprint CHAR(64) NOT NULL,
    food_subtotal NUMERIC(12,2) NOT NULL,
    platform_fee NUMERIC(12,2) NOT NULL,
    food_tax_added NUMERIC(12,2) NOT NULL,
    platform_tax_included NUMERIC(12,2) NOT NULL,
    delivery_tax_included NUMERIC(12,2) NOT NULL,
    tax_amount NUMERIC(12,2) NOT NULL,
    total_tax_amount NUMERIC(12,2) NOT NULL,
    delivery_fee NUMERIC(12,2) NOT NULL,
    grand_total NUMERIC(12,2) NOT NULL,
    charge_policy_id UUID NOT NULL REFERENCES order_schema.charge_policy(id),
    delivery_pricing_version VARCHAR(80) NOT NULL,
    tax_profile_version VARCHAR(80) NOT NULL,
    dropoff_latitude NUMERIC(10,7) NOT NULL,
    dropoff_longitude NUMERIC(10,7) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    consumed_checkout_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_checkout_pricing_quote_coordinates CHECK (
        dropoff_latitude BETWEEN -90 AND 90
        AND dropoff_longitude BETWEEN -180 AND 180
    ),
    CONSTRAINT chk_checkout_pricing_quote_amounts CHECK (
        food_subtotal >= 0
        AND platform_fee >= 0
        AND food_tax_added >= 0
        AND platform_tax_included >= 0
        AND delivery_tax_included >= 0
        AND tax_amount >= 0
        AND total_tax_amount >= 0
        AND delivery_fee >= 0
        AND grand_total >= 0
    )
);

CREATE TABLE order_schema.checkout_pricing_quote_kitchen (
    quote_id UUID NOT NULL REFERENCES order_schema.checkout_pricing_quote(id) ON DELETE CASCADE,
    kitchen_id UUID NOT NULL,
    kitchen_name VARCHAR(160) NOT NULL,
    pickup_latitude NUMERIC(10,7) NOT NULL,
    pickup_longitude NUMERIC(10,7) NOT NULL,
    road_distance_meters BIGINT NOT NULL,
    traffic_duration_seconds BIGINT NOT NULL,
    food_subtotal NUMERIC(12,2) NOT NULL,
    platform_fee NUMERIC(12,2) NOT NULL,
    food_tax_added NUMERIC(12,2) NOT NULL,
    platform_tax_included NUMERIC(12,2) NOT NULL,
    delivery_tax_included NUMERIC(12,2) NOT NULL,
    tax_amount NUMERIC(12,2) NOT NULL,
    base_distance_km NUMERIC(10,3) NOT NULL,
    base_delivery_fee NUMERIC(12,2) NOT NULL,
    extra_distance_km NUMERIC(10,3) NOT NULL,
    extra_per_km NUMERIC(12,2) NOT NULL,
    extra_distance_fee NUMERIC(12,2) NOT NULL,
    delivery_fee NUMERIC(12,2) NOT NULL,
    grand_total NUMERIC(12,2) NOT NULL,
    PRIMARY KEY (quote_id, kitchen_id),
    CONSTRAINT chk_checkout_pricing_quote_kitchen_coordinates CHECK (
        pickup_latitude BETWEEN -90 AND 90
        AND pickup_longitude BETWEEN -180 AND 180
    ),
    CONSTRAINT chk_checkout_pricing_quote_kitchen_route CHECK (
        road_distance_meters >= 0
        AND traffic_duration_seconds >= 0
        AND base_distance_km > 0
        AND base_delivery_fee >= 0
        AND extra_distance_km >= 0
        AND extra_per_km >= 0
        AND extra_distance_fee >= 0
    )
);

ALTER TABLE order_schema.checkout
    ADD COLUMN IF NOT EXISTS pricing_quote_id UUID,
    ADD COLUMN IF NOT EXISTS delivery_pricing_version VARCHAR(80),
    ADD COLUMN IF NOT EXISTS tax_profile_version VARCHAR(80),
    ADD COLUMN IF NOT EXISTS food_tax_added NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS platform_tax_included NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS delivery_tax_included NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS total_tax_amount NUMERIC(12,2);

ALTER TABLE order_schema.customer_order
    ADD COLUMN IF NOT EXISTS pricing_quote_id UUID,
    ADD COLUMN IF NOT EXISTS road_distance_meters BIGINT,
    ADD COLUMN IF NOT EXISTS traffic_duration_seconds BIGINT,
    ADD COLUMN IF NOT EXISTS delivery_pricing_version VARCHAR(80),
    ADD COLUMN IF NOT EXISTS tax_profile_version VARCHAR(80),
    ADD COLUMN IF NOT EXISTS food_tax_added NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS platform_tax_included NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS delivery_tax_included NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS total_tax_amount NUMERIC(12,2);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_checkout_pricing_quote'
          AND conrelid = 'order_schema.checkout'::regclass
    ) THEN
        ALTER TABLE order_schema.checkout
            ADD CONSTRAINT fk_checkout_pricing_quote
            FOREIGN KEY (pricing_quote_id)
            REFERENCES order_schema.checkout_pricing_quote(id);
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_customer_order_pricing_quote'
          AND conrelid = 'order_schema.customer_order'::regclass
    ) THEN
        ALTER TABLE order_schema.customer_order
            ADD CONSTRAINT fk_customer_order_pricing_quote
            FOREIGN KEY (pricing_quote_id)
            REFERENCES order_schema.checkout_pricing_quote(id);
    END IF;
END
$$;

CREATE INDEX idx_checkout_pricing_quote_customer_active
    ON order_schema.checkout_pricing_quote (customer_identity_id, expires_at DESC)
    WHERE consumed_at IS NULL;

CREATE INDEX idx_checkout_pricing_quote_address
    ON order_schema.checkout_pricing_quote (delivery_address_id, created_at DESC);

CREATE INDEX idx_checkout_pricing_quote_kitchen
    ON order_schema.checkout_pricing_quote_kitchen (kitchen_id, quote_id);

CREATE INDEX idx_checkout_pricing_quote_checkout
    ON order_schema.checkout (pricing_quote_id)
    WHERE pricing_quote_id IS NOT NULL;

CREATE INDEX idx_customer_order_pricing_quote
    ON order_schema.customer_order (pricing_quote_id)
    WHERE pricing_quote_id IS NOT NULL;

COMMENT ON TABLE order_schema.checkout_pricing_quote IS
    'Immutable customer-visible pricing quote calculated by Order Service before payment. Quotes expire and can be consumed once.';

COMMENT ON TABLE order_schema.checkout_pricing_quote_kitchen IS
    'Per-chef road-route and pricing inputs used to build a checkout quote. Stores the actual pricing curve values used for auditability.';

COMMENT ON COLUMN order_schema.checkout_pricing_quote.delivery_fee IS
    'Backend-calculated market delivery price derived from Azure Maps road distance. This is not an admin charge-policy field.';

COMMENT ON COLUMN order_schema.checkout_pricing_quote.platform_fee IS
    'Admin-controlled platform fee snapshot. Tax and delivery pricing are not controlled through charge_policy.';
