-- Printing (user, 2026-11-27: "kitchen tickets and receipts: we set them up and they go straight there; documents: choose a
-- printer when you print").
-- A printer installed on a computer (USB, or the computer's own printer list): its name there and which till it hangs on.
ALTER TABLE "device-printer-profiles" ADD COLUMN system_printer_name varchar(200);
ALTER TABLE "device-printer-profiles" ADD COLUMN host_device_id uuid REFERENCES devices (id) ON DELETE SET NULL;

-- What each printer prints: a till's receipts (one printer per till) or a kitchen station's tickets (one printer per station).
CREATE TABLE printer_jobs (
    id uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES restaurants (id),
    printer_id uuid NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    kind varchar(20) NOT NULL CHECK (kind IN ('RECEIPTS', 'KITCHEN')),
    till_id uuid REFERENCES devices (id) ON DELETE CASCADE,
    station_id uuid REFERENCES kds_stations (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    created_by uuid REFERENCES users (id),
    CONSTRAINT ck_printer_jobs_target CHECK ((kind = 'RECEIPTS' AND till_id IS NOT NULL AND station_id IS NULL)
        OR (kind = 'KITCHEN' AND station_id IS NOT NULL AND till_id IS NULL))
);
CREATE UNIQUE INDEX uk_printer_jobs_till ON printer_jobs (till_id) WHERE kind = 'RECEIPTS';
CREATE UNIQUE INDEX uk_printer_jobs_station ON printer_jobs (station_id) WHERE kind = 'KITCHEN';
CREATE INDEX idx_printer_jobs_printer ON printer_jobs (printer_id);

-- When things print by themselves (Admin Hub → Settings → Devices & printing).
ALTER TABLE settings ADD COLUMN print_kitchen_on_send boolean NOT NULL DEFAULT true;
ALTER TABLE settings ADD COLUMN print_bill_on_request boolean NOT NULL DEFAULT true;
ALTER TABLE settings ADD COLUMN print_receipt_after_payment varchar(10) NOT NULL DEFAULT 'ASK'
    CHECK (print_receipt_after_payment IN ('ALWAYS', 'ASK', 'NEVER'));
