-- Which InventoryLocation a line item's ingredients get reserved/deducted from. Nullable for
-- now (existing rows, and any order flow that hasn't started sending it yet), but the intent
-- is for this to become required once the client side always sends it.

alter table order_line_items
    add column location_id uuid;

alter table order_line_items
    add constraint fk_order_line_items_location foreign key (location_id) references inventory_locations (id);

create index idx_order_line_items_location_id on order_line_items (location_id);
