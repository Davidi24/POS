-- Guests who leave without paying (user, 2026-11-01): the order is voided as unpaid, with why, what was still owed and
-- the table it was on. It can be reopened later to take the payment. Who hears about it is a setting.
ALTER TABLE orders
    ADD COLUMN unpaid_reason VARCHAR(20),
    ADD COLUMN unpaid_note VARCHAR(2000),
    ADD COLUMN unpaid_amount NUMERIC(19,2),
    ADD COLUMN unpaid_at TIMESTAMPTZ,
    ADD COLUMN unpaid_by UUID,
    ADD COLUMN unpaid_table_number VARCHAR(30);

CREATE INDEX idx_orders_unpaid_at ON orders (unpaid_at) WHERE unpaid_at IS NOT NULL;

ALTER TABLE settings ADD COLUMN unpaid_alert_roles VARCHAR(500) NOT NULL DEFAULT 'OWNER,MANAGER';
