-- A free table whose booking comes within this many minutes shows a yellow ring (user, 2026-11-01).
ALTER TABLE settings ADD COLUMN booking_coming_minutes INTEGER NOT NULL DEFAULT 30;

-- One open order per table (user, 2026-11-01: "a table can not have two orders"). A table holding several keeps the newest
-- one with dishes. The others: empty ones with no money taken are cancelled; the rest carry on without a table, so no dish
-- or payment is lost.
WITH open_orders AS (
    SELECT o.id, o.table_id, o.opened_at,
           EXISTS (SELECT 1 FROM order_line_items l WHERE l.order_id = o.id AND l.status NOT IN ('CANCELLED', 'VOIDED')) AS has_dishes,
           EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id) AS has_money
    FROM orders o
    WHERE o.table_id IS NOT NULL AND o.status IN ('DRAFT', 'OPEN')
), ranked AS (
    SELECT id, has_dishes, has_money,
           row_number() OVER (PARTITION BY table_id ORDER BY has_dishes DESC, opened_at DESC) AS place
    FROM open_orders
)
UPDATE orders o
SET status = CASE WHEN NOT r.has_dishes AND NOT r.has_money THEN 'CANCELLED' ELSE o.status END,
    table_id = CASE WHEN NOT r.has_dishes AND NOT r.has_money THEN o.table_id ELSE NULL END,
    updated_at = now()
FROM ranked r
WHERE r.id = o.id AND r.place > 1;

CREATE UNIQUE INDEX uk_orders_one_open_per_table ON orders (table_id) WHERE table_id IS NOT NULL AND status IN ('DRAFT', 'OPEN');
