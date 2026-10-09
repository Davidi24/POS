ALTER TABLE settings ADD COLUMN order_tax_rate numeric(7,4) NOT NULL DEFAULT 0 CHECK (order_tax_rate BETWEEN 0 AND 100);
ALTER TABLE settings ADD COLUMN order_tax_inclusive boolean NOT NULL DEFAULT false;
-- Preserve historical orders' previously recorded zero-tax policy.
ALTER TABLE orders ADD COLUMN tax_rate_snapshot numeric(7,4) NOT NULL DEFAULT 0 CHECK (tax_rate_snapshot BETWEEN 0 AND 100);
ALTER TABLE orders ADD COLUMN tax_inclusive_snapshot boolean NOT NULL DEFAULT false;
