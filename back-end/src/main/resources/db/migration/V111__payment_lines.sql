-- Paying by dishes (user, 2026-11-01): which dishes a payment covered, so the next guest can't pay for them again.
CREATE TABLE payment_lines (
    payment_id UUID NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    order_line_item_id UUID NOT NULL REFERENCES order_line_items (id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (payment_id, order_line_item_id)
);
CREATE INDEX idx_payment_lines_line ON payment_lines (order_line_item_id);
