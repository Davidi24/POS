-- Barcode lookups must identify at most one non-deleted item in a restaurant.
-- Stop with a clear message rather than silently choosing between pre-existing duplicate scans.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM inventory_items
        WHERE deleted_at IS NULL AND barcode IS NOT NULL
        GROUP BY restaurant_id, upper(barcode)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot enforce inventory barcode uniqueness: remove duplicate non-deleted barcodes per restaurant first';
    END IF;
END
$$;

CREATE UNIQUE INDEX uk_inventory_items_restaurant_barcode_active
    ON inventory_items (restaurant_id, upper(barcode))
    WHERE deleted_at IS NULL AND barcode IS NOT NULL;
