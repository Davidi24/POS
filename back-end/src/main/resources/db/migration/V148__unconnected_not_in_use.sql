-- "In use means everything has passed" (user, 2026-11-28): a card terminal not connected to a till or tablet, or a cash drawer
-- not plugged into a printer, isn't in use; it waits to be confirmed until it is.
UPDATE devices SET status = 'PROVISIONING'
WHERE device_type IN ('PAYMENT_TERMINAL', 'CASH_DRAWER') AND linked_device_id IS NULL AND status = 'ACTIVE';
