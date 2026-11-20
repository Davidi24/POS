-- A sub-recipe (something the kitchen makes: a sauce, a dough, a cream) now has its item in Inventory → Items.
-- The item carries its name, kind, unit and cost per unit; the recipe says what it's made from and how much a batch makes.
ALTER TABLE recipes ADD COLUMN output_item_id UUID;
ALTER TABLE recipes ADD CONSTRAINT fk_recipes_output_item FOREIGN KEY (output_item_id) REFERENCES inventory_items (id);
ALTER TABLE recipes ADD CONSTRAINT chk_recipes_output_item_not_dish CHECK (output_item_id IS NULL OR recipe_type <> 'FINISHED_DISH');
CREATE UNIQUE INDEX uk_recipes_output_item ON recipes (output_item_id) WHERE output_item_id IS NOT NULL;

-- Every existing preparation (prep batch or sub-recipe) gets its own new item, so nothing is lost and nothing already
-- in stock changes meaning: counted in the batch unit, priced from the recipe's last cost, not counted itself (its
-- ingredients are what's taken from stock), and switched on only while the recipe is in use.
CREATE TEMPORARY TABLE sub_recipe_items ON COMMIT DROP AS
SELECT r.id AS recipe_id, gen_random_uuid() AS item_id
FROM recipes r
WHERE r.recipe_type IN ('PREP_BATCH', 'SUB_RECIPE') AND r.output_item_id IS NULL;

INSERT INTO inventory_items (id, restaurant_id, code, name, description, item_type, base_unit, cost_per_unit,
                             track_inventory, is_active, created_at, updated_at, created_by, updated_by)
SELECT m.item_id,
       r.restaurant_id,
       'MADE_' || upper(substr(replace(r.id::text, '-', ''), 1, 12)),
       r.name,
       r.description,
       'PREPARED_COMPONENT',
       r.yield_unit,
       round(r.theoretical_cost / r.yield_quantity, 4),
       false,
       r.status = 'ACTIVE',
       now(),
       now(),
       r.created_by,
       r.updated_by
FROM sub_recipe_items m
JOIN recipes r ON r.id = m.recipe_id;

UPDATE recipes r
SET output_item_id = m.item_id
FROM sub_recipe_items m
WHERE r.id = m.recipe_id;
