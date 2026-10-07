-- Supports the restaurant-scoped, stable customer page order while excluding soft-deleted rows.
create index idx_customers_active_name_page
    on customers (restaurant_id, first_name, last_name, id)
    where deleted_at is null;
