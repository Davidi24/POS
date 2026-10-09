ALTER TABLE "option-items"
    ADD COLUMN inventory_recipe_id UUID,
    ADD COLUMN inventory_recipe_quantity NUMERIC(12, 3);

ALTER TABLE "option-items"
    ADD CONSTRAINT fk_option_items_inventory_recipe
        FOREIGN KEY (inventory_recipe_id) REFERENCES recipes(id),
    ADD CONSTRAINT chk_option_items_inventory_recipe_usage
        CHECK (
            (inventory_recipe_id IS NULL AND inventory_recipe_quantity IS NULL)
            OR (inventory_recipe_id IS NOT NULL AND inventory_recipe_quantity > 0)
        );

ALTER TABLE order_item_options
    ADD COLUMN inventory_recipe_id_snapshot UUID,
    ADD COLUMN inventory_recipe_quantity_snapshot NUMERIC(12, 3);

ALTER TABLE order_item_options
    ADD CONSTRAINT fk_order_item_options_inventory_recipe_snapshot
        FOREIGN KEY (inventory_recipe_id_snapshot) REFERENCES recipes(id),
    ADD CONSTRAINT chk_order_item_options_inventory_recipe_snapshot
        CHECK (
            (inventory_recipe_id_snapshot IS NULL AND inventory_recipe_quantity_snapshot IS NULL)
            OR (inventory_recipe_id_snapshot IS NOT NULL AND inventory_recipe_quantity_snapshot > 0)
        );
