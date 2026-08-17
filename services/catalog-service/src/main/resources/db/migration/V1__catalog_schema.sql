CREATE SCHEMA IF NOT EXISTS catalog_schema;

CREATE TABLE catalog_schema.kitchen_profile (
    id              UUID PRIMARY KEY,
    identity_id     UUID NOT NULL UNIQUE,
    kitchen_name    VARCHAR(160) NOT NULL,
    display_name    VARCHAR(160),
    description     TEXT,
    phone_number    VARCHAR(32),
    email           VARCHAR(320),
    address_line1   VARCHAR(255) NOT NULL,
    address_line2   VARCHAR(255),
    landmark        VARCHAR(160),
    area_name       VARCHAR(120),
    city            VARCHAR(80) NOT NULL,
    state           VARCHAR(80) NOT NULL,
    postal_code     VARCHAR(16),
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    status          VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_catalog_kitchen_status ON catalog_schema.kitchen_profile (status);
CREATE INDEX idx_catalog_kitchen_city_area ON catalog_schema.kitchen_profile (city, area_name);
CREATE INDEX idx_catalog_kitchen_lat_lng ON catalog_schema.kitchen_profile (latitude, longitude);
CREATE INDEX idx_catalog_kitchen_identity ON catalog_schema.kitchen_profile (identity_id);

CREATE TABLE catalog_schema.menu_item (
    id                          UUID PRIMARY KEY,
    kitchen_id                  UUID NOT NULL REFERENCES catalog_schema.kitchen_profile(id) ON DELETE CASCADE,
    item_name                   VARCHAR(160) NOT NULL,
    description                 TEXT,
    category                    VARCHAR(80) NOT NULL,
    food_type                   VARCHAR(32) NOT NULL,
    price                       NUMERIC(10,2) NOT NULL,
    currency                    VARCHAR(3) NOT NULL DEFAULT 'INR',
    serves_count                INTEGER,
    preparation_time_minutes    INTEGER,
    spice_level                 VARCHAR(32),
    is_available                BOOLEAN NOT NULL DEFAULT false,
    status                      VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_catalog_menu_item_kitchen ON catalog_schema.menu_item (kitchen_id);
CREATE INDEX idx_catalog_menu_item_status_available ON catalog_schema.menu_item (status, is_available);
CREATE INDEX idx_catalog_menu_item_category ON catalog_schema.menu_item (category);

CREATE TABLE catalog_schema.menu_item_image (
    id                  UUID PRIMARY KEY,
    menu_item_id         UUID NOT NULL REFERENCES catalog_schema.menu_item(id) ON DELETE CASCADE,
    blob_container      VARCHAR(120) NOT NULL,
    blob_name           VARCHAR(512) NOT NULL,
    content_type        VARCHAR(120) NOT NULL,
    file_size_bytes     BIGINT NOT NULL,
    public_url          TEXT,
    sort_order          INTEGER NOT NULL DEFAULT 0,
    is_primary          BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_catalog_menu_item_image_item ON catalog_schema.menu_item_image (menu_item_id);
CREATE INDEX idx_catalog_menu_item_image_primary ON catalog_schema.menu_item_image (menu_item_id, is_primary);

CREATE TABLE catalog_schema.menu_item_availability_audit (
    id                  UUID PRIMARY KEY,
    menu_item_id         UUID NOT NULL REFERENCES catalog_schema.menu_item(id) ON DELETE CASCADE,
    chef_identity_id     UUID NOT NULL,
    old_available       BOOLEAN NOT NULL,
    new_available       BOOLEAN NOT NULL,
    reason              VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_catalog_availability_audit_item_time ON catalog_schema.menu_item_availability_audit (menu_item_id, created_at DESC);

CREATE TABLE catalog_schema.service_area_policy (
    id                  UUID PRIMARY KEY,
    city                VARCHAR(80) NOT NULL,
    area_name           VARCHAR(120) NOT NULL,
    default_radius_km   NUMERIC(5,2) NOT NULL,
    max_radius_km       NUMERIC(5,2) NOT NULL,
    is_active           BOOLEAN NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_catalog_service_area_policy UNIQUE (city, area_name)
);

INSERT INTO catalog_schema.service_area_policy (id, city, area_name, default_radius_km, max_radius_km, is_active)
VALUES ('10000000-0000-0000-0000-000000000001', 'Hyderabad', 'DEFAULT', 10.00, 15.00, true)
ON CONFLICT (city, area_name) DO NOTHING;
