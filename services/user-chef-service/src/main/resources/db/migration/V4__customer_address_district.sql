ALTER TABLE customer_address
    ADD COLUMN IF NOT EXISTS district_name varchar(120);

COMMENT ON COLUMN customer_address.district_name IS
    'Administrative district resolved for the delivery point. Legacy rows may be null until customer confirmation.';
