ALTER TABLE subscription_schema.subscription_plan_schedule_draft_item
    ADD COLUMN IF NOT EXISTS menu_item_name_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS menu_item_category_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS menu_item_food_type_snapshot VARCHAR(40),
    ADD COLUMN IF NOT EXISTS menu_item_price_snapshot NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS menu_item_currency_snapshot VARCHAR(3);

ALTER TABLE subscription_schema.subscription_plan_schedule_item
    ADD COLUMN IF NOT EXISTS menu_item_name_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS menu_item_category_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS menu_item_food_type_snapshot VARCHAR(40),
    ADD COLUMN IF NOT EXISTS menu_item_price_snapshot NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS menu_item_currency_snapshot VARCHAR(3);

ALTER TABLE subscription_schema.subscription_plan_schedule_draft_item
    ADD CONSTRAINT ck_subscription_plan_schedule_draft_item_price_snapshot
        CHECK (menu_item_price_snapshot IS NULL OR menu_item_price_snapshot >= 0),
    ADD CONSTRAINT ck_subscription_plan_schedule_draft_item_currency_snapshot
        CHECK (menu_item_currency_snapshot IS NULL OR char_length(menu_item_currency_snapshot) = 3);

ALTER TABLE subscription_schema.subscription_plan_schedule_item
    ADD CONSTRAINT ck_subscription_plan_schedule_item_price_snapshot
        CHECK (menu_item_price_snapshot IS NULL OR menu_item_price_snapshot >= 0),
    ADD CONSTRAINT ck_subscription_plan_schedule_item_currency_snapshot
        CHECK (menu_item_currency_snapshot IS NULL OR char_length(menu_item_currency_snapshot) = 3);
