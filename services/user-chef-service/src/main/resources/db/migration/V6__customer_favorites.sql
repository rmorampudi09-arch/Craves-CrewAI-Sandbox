CREATE TABLE customer_favorite_menu_item (
    identity_id UUID NOT NULL,
    menu_item_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (identity_id, menu_item_id)
);

CREATE INDEX idx_customer_favorite_identity_created
    ON customer_favorite_menu_item (identity_id, created_at DESC);
