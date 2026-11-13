-- Inventory kinds the restaurant manages (user, 2026-10-30: "the default kinds are there by default, I can just remove
-- them or add new ones"). Built-in kinds stay codes on the rows (item_type / location_type). A removed built-in kind is a
-- row here with builtin_code set and deleted_at filled in; a restaurant's own kind is a row with a name, used through
-- item_type / location_type 'CUSTOM' + kind_id. 'UNCATEGORIZED' is where things go when their kind or place is deleted.
create table inventory_kinds (
    id uuid not null,
    restaurant_id uuid not null,
    applies_to varchar(10) not null,
    name varchar(60) not null,
    builtin_code varchar(30),
    deleted_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    primary key (id),
    constraint fk_inventory_kinds_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint ck_inventory_kinds_fields check (applies_to in ('ITEM', 'PLACE') and char_length(btrim(name)) > 0)
);
create index idx_inventory_kinds_restaurant on inventory_kinds (restaurant_id, applies_to);
-- One live kind per name, and one "removed" mark per built-in kind.
create unique index uk_inventory_kinds_live_name on inventory_kinds (restaurant_id, applies_to, lower(name))
    where deleted_at is null and builtin_code is null;
create unique index uk_inventory_kinds_builtin on inventory_kinds (restaurant_id, applies_to, builtin_code)
    where builtin_code is not null;

alter table inventory_items add column kind_id uuid;
alter table inventory_items add constraint fk_inventory_items_kind foreign key (kind_id) references inventory_kinds(id);
alter table inventory_items drop constraint if exists inventory_items_check;
alter table inventory_items add constraint ck_inventory_items_fields check (
    char_length(btrim(code)) > 0
    and char_length(btrim(name)) > 0
    and item_type in ('INGREDIENT', 'PREPARED_COMPONENT', 'FINISHED_GOOD', 'BEVERAGE', 'ALCOHOL', 'PACKAGING', 'SUPPLY',
                      'UNCATEGORIZED', 'CUSTOM')
    and base_unit in ('EACH', 'GRAM', 'KILOGRAM', 'MILLILITER', 'LITER', 'OUNCE', 'POUND', 'CUP', 'TABLESPOON', 'TEASPOON',
                      'PORTION', 'CASE', 'BOTTLE', 'PACK', 'TRAY')
    and cost_per_unit >= 0
    and (reorder_point is null or reorder_point >= 0)
    and (par_level is null or par_level >= 0)
    and (item_type <> 'CUSTOM' or kind_id is not null)
);

-- Places can be deleted (soft: their movements and counts keep pointing at them) and have their own kinds too.
alter table inventory_locations add column kind_id uuid;
alter table inventory_locations add column deleted_at timestamptz;
alter table inventory_locations add constraint fk_inventory_locations_kind foreign key (kind_id) references inventory_kinds(id);
alter table inventory_locations drop constraint if exists inventory_locations_check;
alter table inventory_locations add constraint ck_inventory_locations_fields check (
    char_length(btrim(code)) > 0
    and char_length(btrim(name)) > 0
    and location_type in ('BRANCH_STORAGE', 'KITCHEN', 'BAR', 'WALK_IN', 'FREEZER', 'DRY_STORAGE', 'CENTRAL_WAREHOUSE', 'TRANSIT',
                          'UNCATEGORIZED', 'CUSTOM')
    and (location_type <> 'CUSTOM' or kind_id is not null)
);
create index idx_inventory_locations_deleted_at on inventory_locations (deleted_at);
