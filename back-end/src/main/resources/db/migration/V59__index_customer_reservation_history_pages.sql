-- Supports restaurant/customer scoped history pages with a stable newest-first order.
create index idx_reservations_customer_history_page
    on reservations (restaurant_id, customer_id, reservation_start desc, id desc)
    where customer_id is not null;
