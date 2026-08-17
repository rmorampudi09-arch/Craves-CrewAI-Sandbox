INSERT INTO delivery_schema.delivery_provider
    (provider_id, display_name, adapter_type, is_active, service_areas, capabilities, created_at, updated_at)
VALUES
    (
        'borzo',
        'Borzo',
        'BORZO_BUSINESS_API_1_8',
        FALSE,
        '[]'::jsonb,
        '{
          "QUOTE": true,
          "CREATE_DELIVERY": true,
          "CANCEL": true,
          "TRACK": true,
          "WEBHOOK": true,
          "THERMOBOX_REQUEST": true,
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

-- Intentionally do not activate Borzo here. Activation is an operational decision after
-- callback verification, sandbox integration tests, commercial approval and production KYC.
