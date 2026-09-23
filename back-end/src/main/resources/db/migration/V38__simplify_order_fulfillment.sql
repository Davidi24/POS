-- Fulfillment tracks service progress only. Lifecycle cancellation stays in status.
-- Flyway runs this constraint/data change in one transaction.
-- Retain the exact legacy values for audit/recovery before normalizing them.
CREATE TABLE order_fulfillment_legacy (
    order_id uuid PRIMARY KEY,
    previous_fulfillment_status varchar(30) NOT NULL,
    migrated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO order_fulfillment_legacy (order_id, previous_fulfillment_status)
SELECT id, fulfillment_status FROM orders
WHERE fulfillment_status IN ('DELIVERED', 'CANCELLED');

ALTER TABLE orders DROP CONSTRAINT orders_check;

UPDATE orders SET fulfillment_status = 'FULFILLED'
WHERE fulfillment_status = 'DELIVERED';

-- The previous CANCELLED value overwrote service progress, so it cannot be
-- recovered. Use the neutral default; cancelled/voided progress is hidden by UI.
UPDATE orders SET fulfillment_status = 'PENDING'
WHERE fulfillment_status = 'CANCELLED';

ALTER TABLE orders ADD CONSTRAINT ck_orders_valid_state CHECK (
        char_length(btrim(order_number)) > 0
        AND char_length(currency) = 3
        AND order_type IN ('DINE_IN', 'TAKEAWAY', 'DELIVERY')
        AND source IN ('POS', 'WEB', 'MOBILE', 'QR_TABLE', 'KIOSK', 'PHONE', 'THIRD_PARTY')
        AND status IN ('DRAFT', 'OPEN', 'CLOSED', 'CANCELLED', 'VOIDED')
        AND fulfillment_status IN (
            'PENDING',
            'IN_PREPARATION',
            'READY',
            'PARTIALLY_FULFILLED',
            'FULFILLED'
        )
        AND payment_status IN (
            'UNPAID',
            'PARTIALLY_PAID',
            'PAID',
            'PARTIALLY_REFUNDED',
            'REFUNDED',
            'VOIDED'
        )
        AND guest_count > 0
        AND subtotal >= 0
        AND discount_total >= 0
        AND tax_total >= 0
        AND service_charge_total >= 0
        AND total >= 0
        AND (closed_at IS NULL OR closed_at >= opened_at)
        AND (
            status = 'CLOSED'
            OR closed_at IS NULL
        )
        AND (
            reservation_id IS NULL
            OR order_type = 'DINE_IN'
        )
);
