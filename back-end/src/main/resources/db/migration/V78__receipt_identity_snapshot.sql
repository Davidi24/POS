-- Capture the legal receipt identity at the first captured payment (or direct close).
CREATE TABLE order_receipt_identities (
 order_id uuid PRIMARY KEY REFERENCES orders(id),
 identity_json jsonb NOT NULL,
 captured_at timestamptz NOT NULL,
 legacy_backfill boolean NOT NULL DEFAULT false
);
CREATE FUNCTION receipt_identity_at(target_order uuid, at_time timestamptz) RETURNS jsonb
LANGUAGE sql STABLE SET search_path FROM CURRENT AS $$
 SELECT jsonb_build_object(
  'restaurantName',r.name,'legalName',r.legal_name,'branchName',b.name,
  'addressLines',COALESCE((SELECT jsonb_agg(line ORDER BY ordinal) FROM
    (VALUES (1,a.street_line_1),(2,a.street_line_2),(3,btrim(concat_ws(' ',a.postal_code,a.city))),(4,a.country)) lines(ordinal,line)
    WHERE nullif(btrim(line),'') IS NOT NULL),'[]'::jsonb),
  'phone',coalesce(nullif(b.phone,''),r.phone),'taxNumber',t.tax_number,'vatNumber',t.vat_number,
  'showLogo',coalesce(rs.show_logo,false),'logoUrl',CASE WHEN rs.show_logo THEN brand.logo_url END,
  'footerNote',rs.footer_note)
 FROM orders o JOIN restaurants r ON r.id=o.restaurant_id JOIN branches b ON b.id=o.branch_id
 LEFT JOIN LATERAL (
   SELECT q.street_line_1,q.street_line_2,q.postal_code,q.city,q.country FROM (
    SELECT 0 source,is_primary,created_at,id,street_line_1,street_line_2,postal_code,city,country
      FROM "branch-addresses" WHERE branch_id=b.id AND deleted_at IS NULL
    UNION ALL
    SELECT 1 source,is_primary,created_at,id,street_line_1,street_line_2,postal_code,city,country
      FROM "restaurant-addresses" WHERE restaurant_id=r.id AND deleted_at IS NULL AND is_primary=true
   ) q ORDER BY source,is_primary DESC,created_at,id LIMIT 1
 ) a ON true
 LEFT JOIN "restaurant-tax-profiles" t ON t.restaurant_id=r.id AND t.deleted_at IS NULL AND t.is_default
   AND (t.effective_from IS NULL OR t.effective_from<=at_time) AND (t.effective_to IS NULL OR at_time<t.effective_to)
 LEFT JOIN "restaurant-branding" brand ON brand.restaurant_id=r.id AND brand.deleted_at IS NULL
 LEFT JOIN settings s ON s.restaurant_id=r.id LEFT JOIN "settings-receipts" rs ON rs.settings_id=s.id
 WHERE o.id=target_order
$$;
CREATE FUNCTION capture_paid_receipt_identity() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
 IF NEW.status IN ('CAPTURED','PARTIALLY_REFUNDED','REFUNDED') THEN
  INSERT INTO order_receipt_identities(order_id,identity_json,captured_at)
    VALUES(NEW.order_id,receipt_identity_at(NEW.order_id,coalesce(NEW.paid_at,now())),coalesce(NEW.paid_at,now()))
    ON CONFLICT(order_id) DO NOTHING;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER capture_paid_receipt_identity AFTER INSERT OR UPDATE OF status ON payments
 FOR EACH ROW EXECUTE FUNCTION capture_paid_receipt_identity();
CREATE FUNCTION capture_closed_receipt_identity() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
 IF NEW.status='CLOSED' THEN
  INSERT INTO order_receipt_identities(order_id,identity_json,captured_at)
    VALUES(NEW.id,receipt_identity_at(NEW.id,coalesce(NEW.closed_at,now())),coalesce(NEW.closed_at,now()))
    ON CONFLICT(order_id) DO NOTHING;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER capture_closed_receipt_identity AFTER INSERT OR UPDATE OF status ON orders
 FOR EACH ROW EXECUTE FUNCTION capture_closed_receipt_identity();
-- Historical edits before this migration cannot be reconstructed. Mark that limitation explicitly.
INSERT INTO order_receipt_identities(order_id,identity_json,captured_at,legacy_backfill)
 SELECT o.id,receipt_identity_at(o.id,coalesce(o.closed_at,now())),now(),true FROM orders o
 WHERE o.status='CLOSED' OR EXISTS(SELECT 1 FROM payments p WHERE p.order_id=o.id AND p.status IN ('CAPTURED','PARTIALLY_REFUNDED','REFUNDED'))
 ON CONFLICT(order_id) DO NOTHING;
