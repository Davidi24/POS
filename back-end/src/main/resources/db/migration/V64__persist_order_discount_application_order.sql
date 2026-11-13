ALTER TABLE order_discounts
    ADD COLUMN discount_sequence integer;

WITH ranked_discounts AS (
    SELECT id,
           row_number() OVER (PARTITION BY order_id ORDER BY created_at, id) - 1 AS sequence
    FROM order_discounts
)
UPDATE order_discounts discount
SET discount_sequence = ranked_discounts.sequence
FROM ranked_discounts
WHERE ranked_discounts.id = discount.id;

ALTER TABLE order_discounts
    ALTER COLUMN discount_sequence SET NOT NULL,
    ADD CONSTRAINT ck_order_discounts_sequence_nonnegative CHECK (discount_sequence >= 0);

CREATE INDEX idx_order_discounts_order_sequence
    ON order_discounts (order_id, discount_sequence);
