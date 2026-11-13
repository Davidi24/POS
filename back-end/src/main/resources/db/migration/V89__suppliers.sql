-- Suppliers are kept by the server (user, 2026-10-30: the item form picks one of the suppliers you created instead of a
-- typed name): a supplier, their places (warehouses you order from, with the days they deliver) and the items you buy
-- from them (their code and price per unit of the item). Orders and price lists stay in the app for now.
CREATE TABLE suppliers (
    id            UUID         NOT NULL PRIMARY KEY,
    restaurant_id UUID         NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    name          VARCHAR(150) NOT NULL,
    contact_name  VARCHAR(150),
    phone         VARCHAR(40),
    email         VARCHAR(150),
    address       VARCHAR(240),
    notes         VARCHAR(2000),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    UUID,
    updated_by    UUID,
    CONSTRAINT ck_suppliers_name CHECK (char_length(btrim(name)) > 0)
);
CREATE UNIQUE INDEX uk_suppliers_live_name ON suppliers (restaurant_id, lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE supplier_places (
    id            UUID         NOT NULL PRIMARY KEY,
    supplier_id   UUID         NOT NULL REFERENCES suppliers (id) ON DELETE CASCADE,
    name          VARCHAR(120) NOT NULL,
    address       VARCHAR(240),
    phone         VARCHAR(40),
    -- ISO weekdays 1 (Monday) … 7, comma separated ("1,3,5").
    delivery_days VARCHAR(20),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_supplier_places_name CHECK (char_length(btrim(name)) > 0)
);
CREATE INDEX idx_supplier_places_supplier ON supplier_places (supplier_id);

CREATE TABLE supplier_items (
    id                UUID          NOT NULL PRIMARY KEY,
    supplier_id       UUID          NOT NULL REFERENCES suppliers (id) ON DELETE CASCADE,
    inventory_item_id UUID          NOT NULL REFERENCES inventory_items (id) ON DELETE CASCADE,
    code              VARCHAR(100),
    price             NUMERIC(19, 4),
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_supplier_items UNIQUE (supplier_id, inventory_item_id),
    CONSTRAINT ck_supplier_items_price CHECK (price IS NULL OR price >= 0)
);
CREATE INDEX idx_supplier_items_item ON supplier_items (inventory_item_id);

-- The item's own supplier (its supplier_name stays as the supplier's name, for history and old clients).
ALTER TABLE inventory_items ADD COLUMN supplier_id UUID REFERENCES suppliers (id) ON DELETE SET NULL;
CREATE INDEX idx_inventory_items_supplier ON inventory_items (supplier_id);

-- Every name typed on an item so far becomes a supplier of its restaurant (one per name, any case), linked to its items
-- with their code and cost.
INSERT INTO suppliers (id, restaurant_id, name, created_at, updated_at)
SELECT gen_random_uuid(), named.restaurant_id, named.name, now(), now()
FROM (
    SELECT DISTINCT ON (restaurant_id, lower(regexp_replace(btrim(supplier_name), '\s+', ' ', 'g')))
           restaurant_id, regexp_replace(btrim(supplier_name), '\s+', ' ', 'g') AS name
    FROM inventory_items
    WHERE supplier_name IS NOT NULL AND btrim(supplier_name) <> ''
    ORDER BY restaurant_id, lower(regexp_replace(btrim(supplier_name), '\s+', ' ', 'g')), created_at
) named;

UPDATE inventory_items i SET supplier_id = s.id
FROM suppliers s
WHERE s.restaurant_id = i.restaurant_id
  AND i.supplier_name IS NOT NULL
  AND lower(s.name) = lower(regexp_replace(btrim(i.supplier_name), '\s+', ' ', 'g'));

INSERT INTO supplier_items (id, supplier_id, inventory_item_id, code, price, created_at, updated_at)
SELECT gen_random_uuid(), i.supplier_id, i.id, i.supplier_sku, NULLIF(i.cost_per_unit, 0), now(), now()
FROM inventory_items i
WHERE i.supplier_id IS NOT NULL AND i.deleted_at IS NULL;
