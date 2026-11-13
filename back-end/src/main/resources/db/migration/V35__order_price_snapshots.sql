ALTER TABLE order_line_items ADD COLUMN variant_price_delta_snapshot numeric(19,2) NOT NULL DEFAULT 0;
-- Recover the variant delta from saved totals, preserving the price charged before this migration.
UPDATE order_line_items l SET variant_price_delta_snapshot =
    (l.price_delta_total - COALESCE((SELECT SUM(o.price_delta_snapshot * o.quantity) FROM order_item_options o WHERE o.order_line_item_id = l.id), 0)) / l.quantity;

-- Old order lines charged options once per line; preserve their recorded totals.
ALTER TABLE order_line_items ADD COLUMN options_per_unit boolean NOT NULL DEFAULT false;
ALTER TABLE order_line_items ALTER COLUMN options_per_unit SET DEFAULT true;
