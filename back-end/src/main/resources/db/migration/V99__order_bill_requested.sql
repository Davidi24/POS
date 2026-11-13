-- The guests asked for the bill (user, 2026-10-31): the table shows Bill Pending and can't be merged. Ordering more clears it.
ALTER TABLE orders ADD COLUMN bill_requested_at TIMESTAMPTZ;
