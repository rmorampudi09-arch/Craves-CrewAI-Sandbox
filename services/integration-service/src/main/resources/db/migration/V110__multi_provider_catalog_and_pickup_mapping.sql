-- Register delivery providers without activating them and provide a provider-neutral mapping from
-- Craves kitchen_id to an external provider pickup-location identifier.

INSERT INTO delivery_schema.delivery_provider
    (provider_id, display_name, adapter_type, is_active, service_areas, capabilities, created_at, updated_at)
VALUES
    (
        'shiprocket',
        'Shiprocket Quick',
        'SHIPROCKET_EXTERNAL_API_V1',
        FALSE,
        '[]'::jsonb,
        '{
          "QUOTE": true,
          "CREATE_DELIVERY": true,
          "CANCEL": true,
          "TRACK": true,
          "WEBHOOK": true,
          "HYPERLOCAL_FILTER": true,
          "RIDER_LEVEL_CANDIDATES": false,
          "CREATE_REQUIRES_PRODUCTION_GATE": true
        }'::jsonb,
        now(),
        now()
    ),
    (
        'shadowfax',
        'Shadowfax',
        'VENDOR_PRIVATE_API_REQUIRED',
        FALSE,
        '[]'::jsonb,
        '{
          "VENDOR_API_CONTRACT_REQUIRED": true,
          "RIDER_LEVEL_CANDIDATES": false
        }'::jsonb,
        now(),
        now()
    ),
    (
        'porter',
        'Porter',
        'ENTERPRISE_API_REQUIRED',
        FALSE,
        '[]'::jsonb,
        '{
          "ENTERPRISE_API_ONBOARDING_REQUIRED": true,
          "RIDER_LEVEL_CANDIDATES": false
        }'::jsonb,
        now(),
        now()
    ),
    (
        'delhivery',
        'Delhivery',
        'INTRACITY_API_PRODUCT_REQUIRED',
        FALSE,
        '[]'::jsonb,
        '{
          "INTRACITY_API_PRODUCT_REQUIRED": true,
          "RIDER_LEVEL_CANDIDATES": false
        }'::jsonb,
        now(),
        now()
    )
ON CONFLICT (provider_id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    adapter_type = EXCLUDED.adapter_type,
    capabilities = EXCLUDED.capabilities,
    updated_at = now();

-- Deliberately preserve is_active on conflict. Migrations must never activate a provider.

CREATE TABLE IF NOT EXISTS delivery_schema.delivery_provider_pickup_location (
    provider_id                 VARCHAR(80) NOT NULL
                                REFERENCES delivery_schema.delivery_provider(provider_id),
    pickup_location_reference   UUID NOT NULL,
    external_location_code      VARCHAR(200) NOT NULL,
    is_verified                 BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at                 TIMESTAMPTZ,
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (provider_id, pickup_location_reference),
    CONSTRAINT ck_delivery_provider_pickup_code_not_blank CHECK (
        length(btrim(external_location_code)) > 0
    ),
    CONSTRAINT ck_delivery_provider_pickup_metadata_object CHECK (
        jsonb_typeof(metadata) = 'object'
    ),
    CONSTRAINT ck_delivery_provider_pickup_verified_time CHECK (
        NOT is_verified OR verified_at IS NOT NULL
    )
);

CREATE INDEX IF NOT EXISTS ix_delivery_provider_pickup_location_external
    ON delivery_schema.delivery_provider_pickup_location (provider_id, external_location_code);
