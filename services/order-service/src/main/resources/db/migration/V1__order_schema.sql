CREATE SCHEMA IF NOT EXISTS order_schema;

CREATE TABLE order_schema.charge_policy (
    id UUID PRIMARY KEY,
    policy_name VARCHAR(120) NOT NULL,
    platform_fee_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    platform_fee_flat NUMERIC(10,2) NOT NULL DEFAULT 0,
    tax_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    delivery_fee_flat NUMERIC(10,2) NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT false,
    created_by_identity_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uk_order_active_charge_policy ON order_schema.charge_policy (is_active) WHERE is_active = true;

INSERT INTO order_schema.charge_policy (id, policy_name, platform_fee_percent, platform_fee_flat, tax_percent, delivery_fee_flat, is_active)
VALUES ('20000000-0000-0000-0000-000000000001', 'V1_ZERO_FEE_CHECKOUT', 0, 0, 0, 0, true)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE order_schema.cart (
    id UUID PRIMARY KEY,
    customer_identity_id UUID NOT NULL UNIQUE,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_schema.cart_item (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES order_schema.cart(id) ON DELETE CASCADE,
    menu_item_id UUID NOT NULL,
    kitchen_id UUID,
    item_name_snapshot VARCHAR(160),
    kitchen_name_snapshot VARCHAR(160),
    unit_price_snapshot NUMERIC(10,2),
    currency_snapshot VARCHAR(3) NOT NULL DEFAULT 'INR',
    quantity INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_cart_item_quantity CHECK (quantity > 0),
    CONSTRAINT uk_cart_menu_item UNIQUE (cart_id, menu_item_id)
);

CREATE TABLE order_schema.checkout (
    id UUID PRIMARY KEY,
    customer_identity_id UUID NOT NULL,
    status VARCHAR(40) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    food_subtotal NUMERIC(10,2) NOT NULL,
    platform_fee NUMERIC(10,2) NOT NULL,
    tax_amount NUMERIC(10,2) NOT NULL,
    delivery_fee NUMERIC(10,2) NOT NULL,
    grand_total NUMERIC(10,2) NOT NULL,
    charge_policy_id UUID REFERENCES order_schema.charge_policy(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_schema.customer_order (
    id UUID PRIMARY KEY,
    checkout_id UUID NOT NULL,
    customer_identity_id UUID NOT NULL,
    kitchen_id UUID NOT NULL,
    kitchen_name_snapshot VARCHAR(160),
    status VARCHAR(40) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    food_subtotal NUMERIC(10,2) NOT NULL,
    platform_fee NUMERIC(10,2) NOT NULL,
    tax_amount NUMERIC(10,2) NOT NULL,
    delivery_fee NUMERIC(10,2) NOT NULL,
    grand_total NUMERIC(10,2) NOT NULL,
    chef_response_note VARCHAR(255),
    prep_time_minutes INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_schema.order_item (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES order_schema.customer_order(id) ON DELETE CASCADE,
    menu_item_id UUID NOT NULL,
    item_name_snapshot VARCHAR(160) NOT NULL,
    category_snapshot VARCHAR(80),
    food_type_snapshot VARCHAR(32),
    unit_price_snapshot NUMERIC(10,2) NOT NULL,
    quantity INTEGER NOT NULL,
    line_total NUMERIC(10,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_schema.order_status_history (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES order_schema.customer_order(id) ON DELETE CASCADE,
    old_status VARCHAR(40),
    new_status VARCHAR(40) NOT NULL,
    actor_identity_id UUID,
    reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_cart_item_cart ON order_schema.cart_item (cart_id);
CREATE INDEX idx_order_checkout_customer ON order_schema.checkout (customer_identity_id, created_at DESC);
CREATE INDEX idx_order_customer_order_customer ON order_schema.customer_order (customer_identity_id, created_at DESC);
CREATE INDEX idx_order_customer_order_kitchen ON order_schema.customer_order (kitchen_id, created_at DESC);
CREATE INDEX idx_order_customer_order_status ON order_schema.customer_order (status);
CREATE INDEX idx_order_customer_order_checkout ON order_schema.customer_order (checkout_id);
CREATE INDEX idx_order_item_order ON order_schema.order_item (order_id);
