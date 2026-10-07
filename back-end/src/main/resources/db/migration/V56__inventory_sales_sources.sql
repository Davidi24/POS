create table inventory_sales_sources (
    id uuid not null primary key,
    restaurant_id uuid not null,
    branch_id uuid not null,
    inventory_item_id uuid not null,
    location_id uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_inventory_sales_sources_branch_item unique (branch_id, inventory_item_id),
    constraint fk_inventory_sales_sources_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_inventory_sales_sources_branch foreign key (branch_id) references branches(id),
    constraint fk_inventory_sales_sources_item foreign key (inventory_item_id) references inventory_items(id),
    constraint fk_inventory_sales_sources_location foreign key (location_id) references inventory_locations(id)
);

create index idx_inventory_sales_sources_restaurant on inventory_sales_sources (restaurant_id);
create index idx_inventory_sales_sources_location on inventory_sales_sources (location_id);
