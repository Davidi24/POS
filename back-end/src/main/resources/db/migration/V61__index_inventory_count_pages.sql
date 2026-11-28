create index idx_inventory_counts_restaurant_created_page
    on inventory_counts (restaurant_id, created_at desc, id desc);

create index idx_inventory_counts_restaurant_status_created_page
    on inventory_counts (restaurant_id, status, created_at desc, id desc);
