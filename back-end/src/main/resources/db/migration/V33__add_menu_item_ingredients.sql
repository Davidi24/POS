create table "menu-item-ingredients" (
    menu_item_id uuid not null references "menu-items"(id) on delete cascade,
    display_order integer not null,
    ingredient varchar(150) not null,
    primary key (menu_item_id, display_order)
);

create index idx_menu_item_ingredients_menu_item_id on "menu-item-ingredients" (menu_item_id);
