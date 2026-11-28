-- Card terminals and cash drawers (user, 2026-11-27): a card terminal works only through the till or tablet it's connected to
-- (Bluetooth or cable); a cash drawer is plugged into a receipt printer. Kept on the device row: what it's connected to, how,
-- and the Bluetooth address or cable port. Cash drawers are devices of their own now (they lived only in the app before).
ALTER TABLE devices ADD COLUMN linked_device_id uuid REFERENCES devices (id) ON DELETE SET NULL;
ALTER TABLE devices ADD COLUMN link_type varchar(20) CHECK (link_type IN ('USB', 'BLUETOOTH', 'PRINTER'));
ALTER TABLE devices ADD COLUMN link_address varchar(200);
CREATE INDEX idx_devices_linked ON devices (linked_device_id) WHERE linked_device_id IS NOT NULL;
