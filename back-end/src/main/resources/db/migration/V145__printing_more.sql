-- Printing, second round (user, 2026-11-27): printers have a kind (receipt, kitchen, bar, small receipt, documents, other).
-- A printer plugged into one device (USB, Bluetooth) prints for the others: they leave the ticket here and that device prints it.
ALTER TABLE "device-printer-profiles" ADD COLUMN printer_role varchar(20) NOT NULL DEFAULT 'OTHER'
    CHECK (printer_role IN ('RECEIPT', 'KITCHEN', 'BAR', 'SMALL', 'DOCUMENT', 'OTHER'));

CREATE TABLE print_queue (
    id uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES restaurants (id),
    printer_id uuid NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    host_device_id uuid NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    payload bytea NOT NULL,
    status varchar(10) NOT NULL DEFAULT 'WAITING' CHECK (status IN ('WAITING', 'PRINTING', 'DONE', 'FAILED')),
    problem varchar(300),
    created_by uuid REFERENCES users (id),
    created_at timestamptz NOT NULL,
    picked_at timestamptz,
    done_at timestamptz
);
CREATE INDEX idx_print_queue_host ON print_queue (host_device_id, status, created_at);
