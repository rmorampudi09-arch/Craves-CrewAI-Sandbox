ALTER TABLE order_schema.checkout
    ADD COLUMN IF NOT EXISTS delivery_address_id UUID,
    ADD COLUMN IF NOT EXISTS dropoff_recipient_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS dropoff_contact_phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS dropoff_address_line1 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dropoff_address_line2 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dropoff_landmark VARCHAR(160),
    ADD COLUMN IF NOT EXISTS dropoff_area_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS dropoff_city VARCHAR(120),
    ADD COLUMN IF NOT EXISTS dropoff_state VARCHAR(120),
    ADD COLUMN IF NOT EXISTS dropoff_postal_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS dropoff_latitude NUMERIC(10,7),
    ADD COLUMN IF NOT EXISTS dropoff_longitude NUMERIC(10,7);

ALTER TABLE order_schema.customer_order
    ADD COLUMN IF NOT EXISTS delivery_address_id UUID,
    ADD COLUMN IF NOT EXISTS dropoff_recipient_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS dropoff_contact_phone VARCHAR(20),
    ADD COLUMN IF NOT EXISTS dropoff_address_line1 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dropoff_address_line2 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dropoff_landmark VARCHAR(160),
    ADD COLUMN IF NOT EXISTS dropoff_area_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS dropoff_city VARCHAR(120),
    ADD COLUMN IF NOT EXISTS dropoff_state VARCHAR(120),
    ADD COLUMN IF NOT EXISTS dropoff_postal_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS dropoff_latitude NUMERIC(10,7),
    ADD COLUMN IF NOT EXISTS dropoff_longitude NUMERIC(10,7),
    ADD COLUMN IF NOT EXISTS pickup_phone_number VARCHAR(20),
    ADD COLUMN IF NOT EXISTS pickup_email VARCHAR(320),
    ADD COLUMN IF NOT EXISTS pickup_address_line1 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pickup_address_line2 VARCHAR(255),
    ADD COLUMN IF NOT EXISTS pickup_landmark VARCHAR(160),
    ADD COLUMN IF NOT EXISTS pickup_area_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS pickup_city VARCHAR(120),
    ADD COLUMN IF NOT EXISTS pickup_state VARCHAR(120),
    ADD COLUMN IF NOT EXISTS pickup_postal_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS pickup_latitude NUMERIC(10,7),
    ADD COLUMN IF NOT EXISTS pickup_longitude NUMERIC(10,7);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_checkout_dropoff_snapshot_complete'
          AND conrelid = 'order_schema.checkout'::regclass
    ) THEN
        ALTER TABLE order_schema.checkout
            ADD CONSTRAINT chk_checkout_dropoff_snapshot_complete
            CHECK (
                delivery_address_id IS NULL
                OR (
                    dropoff_recipient_name IS NOT NULL
                    AND dropoff_contact_phone IS NOT NULL
                    AND dropoff_address_line1 IS NOT NULL
                    AND dropoff_area_name IS NOT NULL
                    AND dropoff_city IS NOT NULL
                    AND dropoff_state IS NOT NULL
                    AND dropoff_postal_code IS NOT NULL
                    AND dropoff_latitude IS NOT NULL
                    AND dropoff_longitude IS NOT NULL
                    AND dropoff_latitude BETWEEN -90 AND 90
                    AND dropoff_longitude BETWEEN -180 AND 180
                )
            ) NOT VALID;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_order_dropoff_snapshot_complete'
          AND conrelid = 'order_schema.customer_order'::regclass
    ) THEN
        ALTER TABLE order_schema.customer_order
            ADD CONSTRAINT chk_order_dropoff_snapshot_complete
            CHECK (
                delivery_address_id IS NULL
                OR (
                    dropoff_recipient_name IS NOT NULL
                    AND dropoff_contact_phone IS NOT NULL
                    AND dropoff_address_line1 IS NOT NULL
                    AND dropoff_area_name IS NOT NULL
                    AND dropoff_city IS NOT NULL
                    AND dropoff_state IS NOT NULL
                    AND dropoff_postal_code IS NOT NULL
                    AND dropoff_latitude IS NOT NULL
                    AND dropoff_longitude IS NOT NULL
                    AND dropoff_latitude BETWEEN -90 AND 90
                    AND dropoff_longitude BETWEEN -180 AND 180
                )
            ) NOT VALID;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_order_pickup_snapshot_complete'
          AND conrelid = 'order_schema.customer_order'::regclass
    ) THEN
        ALTER TABLE order_schema.customer_order
            ADD CONSTRAINT chk_order_pickup_snapshot_complete
            CHECK (
                pickup_address_line1 IS NULL
                OR (
                    pickup_phone_number IS NOT NULL
                    AND pickup_area_name IS NOT NULL
                    AND pickup_city IS NOT NULL
                    AND pickup_state IS NOT NULL
                    AND pickup_postal_code IS NOT NULL
                    AND pickup_latitude IS NOT NULL
                    AND pickup_longitude IS NOT NULL
                    AND pickup_latitude BETWEEN -90 AND 90
                    AND pickup_longitude BETWEEN -180 AND 180
                )
            ) NOT VALID;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_checkout_delivery_address
    ON order_schema.checkout (delivery_address_id)
    WHERE delivery_address_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_customer_order_delivery_address
    ON order_schema.customer_order (delivery_address_id)
    WHERE delivery_address_id IS NOT NULL;

COMMENT ON COLUMN order_schema.checkout.delivery_address_id IS
    'Source User-Chef saved address identifier selected at checkout; snapshot columns remain immutable.';

COMMENT ON COLUMN order_schema.customer_order.delivery_address_id IS
    'Source saved address identifier copied to each kitchen-specific order; no cross-database foreign key is possible.';
