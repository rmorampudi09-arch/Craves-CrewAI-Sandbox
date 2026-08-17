ALTER TABLE order_schema.order_item
    ADD COLUMN IF NOT EXISTS unit_package_weight_grams_snapshot INTEGER,
    ADD COLUMN IF NOT EXISTS thermobox_required_snapshot BOOLEAN;

ALTER TABLE order_schema.customer_order
    ADD COLUMN IF NOT EXISTS total_package_weight_grams INTEGER,
    ADD COLUMN IF NOT EXISTS thermobox_required BOOLEAN,
    ADD COLUMN IF NOT EXISTS ready_at TIMESTAMPTZ;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_order_item_package_weight_snapshot'
          AND conrelid = 'order_schema.order_item'::regclass
    ) THEN
        ALTER TABLE order_schema.order_item
            ADD CONSTRAINT chk_order_item_package_weight_snapshot
            CHECK (
                unit_package_weight_grams_snapshot IS NULL
                OR unit_package_weight_grams_snapshot > 0
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
        WHERE conname = 'chk_customer_order_total_package_weight'
          AND conrelid = 'order_schema.customer_order'::regclass
    ) THEN
        ALTER TABLE order_schema.customer_order
            ADD CONSTRAINT chk_customer_order_total_package_weight
            CHECK (
                total_package_weight_grams IS NULL
                OR total_package_weight_grams > 0
            )
            NOT VALID;
    END IF;
END
$$;

COMMENT ON COLUMN order_schema.order_item.unit_package_weight_grams_snapshot IS
    'Immutable packaged weight snapshot for one ordered menu-item unit, stored in grams.';

COMMENT ON COLUMN order_schema.order_item.thermobox_required_snapshot IS
    'Immutable thermobox handling snapshot copied from Catalog Service at checkout.';

COMMENT ON COLUMN order_schema.customer_order.total_package_weight_grams IS
    'Chef-specific order weight calculated as sum(unit packaged grams x quantity).';

COMMENT ON COLUMN order_schema.customer_order.thermobox_required IS
    'True when any item in the chef-specific order requires thermobox handling.';

COMMENT ON COLUMN order_schema.customer_order.ready_at IS
    'Expected food-ready timestamp calculated when the chef accepts the order.';
