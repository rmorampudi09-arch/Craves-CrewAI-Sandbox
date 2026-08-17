CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE customer_address
    ADD COLUMN IF NOT EXISTS area_name VARCHAR(160),
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE customer_address
    ADD COLUMN IF NOT EXISTS location geography(Point, 4326)
    GENERATED ALWAYS AS (
        CASE
            WHEN latitude IS NULL OR longitude IS NULL THEN NULL
            ELSE ST_SetSRID(
                ST_MakePoint(longitude::double precision, latitude::double precision),
                4326
            )::geography
        END
    ) STORED;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_customer_address_coordinate_pair'
          AND conrelid = 'customer_address'::regclass
    ) THEN
        ALTER TABLE customer_address
            ADD CONSTRAINT ck_customer_address_coordinate_pair
            CHECK (
                (latitude IS NULL AND longitude IS NULL)
                OR (latitude IS NOT NULL AND longitude IS NOT NULL)
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
        WHERE conname = 'ck_customer_address_latitude_range'
          AND conrelid = 'customer_address'::regclass
    ) THEN
        ALTER TABLE customer_address
            ADD CONSTRAINT ck_customer_address_latitude_range
            CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90)
            NOT VALID;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_customer_address_longitude_range'
          AND conrelid = 'customer_address'::regclass
    ) THEN
        ALTER TABLE customer_address
            ADD CONSTRAINT ck_customer_address_longitude_range
            CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
            NOT VALID;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS ix_customer_address_active_identity
    ON customer_address (identity_id, is_default DESC, updated_at DESC)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS ix_customer_address_location_gist
    ON customer_address USING GIST (location)
    WHERE is_active = true AND location IS NOT NULL;

COMMENT ON COLUMN customer_address.area_name IS
    'Customer-facing locality or area name used in location selectors and delivery snapshots.';

COMMENT ON COLUMN customer_address.is_active IS
    'Soft-delete flag. Inactive addresses cannot be recommended or used for a new checkout.';

COMMENT ON COLUMN customer_address.location IS
    'PostGIS geography point generated from longitude and latitude for distance calculations.';
