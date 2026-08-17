-- Azure Database for PostgreSQL runs Flyway with catalog_schema as the
-- application's default schema. PostGIS core objects such as spatial_ref_sys
-- must be installed in public so they are available consistently to every
-- service sharing craves_business_db.
CREATE EXTENSION IF NOT EXISTS postgis WITH SCHEMA public;

ALTER TABLE catalog_schema.kitchen_profile
    ADD COLUMN IF NOT EXISTS location public.geography(Point, 4326)
    GENERATED ALWAYS AS (
        CASE
            WHEN latitude IS NULL OR longitude IS NULL THEN NULL
            ELSE public.ST_SetSRID(
                public.ST_MakePoint(
                    longitude::double precision,
                    latitude::double precision
                ),
                4326
            )::public.geography
        END
    ) STORED;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_catalog_kitchen_coordinate_pair'
          AND conrelid = 'catalog_schema.kitchen_profile'::regclass
    ) THEN
        ALTER TABLE catalog_schema.kitchen_profile
            ADD CONSTRAINT chk_catalog_kitchen_coordinate_pair
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
        WHERE conname = 'chk_catalog_kitchen_latitude_range'
          AND conrelid = 'catalog_schema.kitchen_profile'::regclass
    ) THEN
        ALTER TABLE catalog_schema.kitchen_profile
            ADD CONSTRAINT chk_catalog_kitchen_latitude_range
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
        WHERE conname = 'chk_catalog_kitchen_longitude_range'
          AND conrelid = 'catalog_schema.kitchen_profile'::regclass
    ) THEN
        ALTER TABLE catalog_schema.kitchen_profile
            ADD CONSTRAINT chk_catalog_kitchen_longitude_range
            CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
            NOT VALID;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_catalog_kitchen_active_location
    ON catalog_schema.kitchen_profile USING GIST (location)
    WHERE status = 'ACTIVE' AND location IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_catalog_menu_item_discovery
    ON catalog_schema.menu_item (kitchen_id, category, item_name, id)
    WHERE status = 'ACTIVE'
      AND is_available = true
      AND unit_package_weight_grams IS NOT NULL
      AND thermobox_required IS NOT NULL;

COMMENT ON COLUMN catalog_schema.kitchen_profile.location IS
    'Generated PostGIS geography point used for radius filtering and nearest-first discovery.';
