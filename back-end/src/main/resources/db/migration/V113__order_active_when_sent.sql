-- An order is Draft only while nothing has gone to the kitchen, and Ready only while no dish has been served.
-- updated_at stays as it is, so the special-case clocks aren't restarted.
UPDATE orders o SET status = 'OPEN'
WHERE o.status = 'DRAFT'
  AND EXISTS (SELECT 1 FROM order_line_items l WHERE l.order_id = o.id AND l.status IN ('FIRED', 'PREPARING', 'READY', 'FULFILLED'));

UPDATE orders o SET fulfillment_status = 'PARTIALLY_FULFILLED'
WHERE o.status IN ('DRAFT', 'OPEN') AND o.fulfillment_status = 'READY'
  AND EXISTS (SELECT 1 FROM order_line_items l WHERE l.order_id = o.id AND l.status = 'FULFILLED');
