-- Pre-orders: dishes a guest picks together with a reservation. They are paid up front and held until
-- shortly before the booking, then sent to the kitchen as a real order so the food is ready on arrival.
-- Cancelled before that point: refunded. Sent and the guest never comes: the payment is kept.
create table pre_orders (
    id uuid not null,
    restaurant_id uuid not null,
    reservation_id uuid not null,
    order_id uuid,
    status varchar(30) not null,
    payment_status varchar(30) not null,
    source varchar(30) not null,
    currency varchar(3) not null,
    subtotal numeric(19, 2) not null,
    tax_total numeric(19, 2) not null,
    service_charge_total numeric(19, 2) not null,
    total numeric(19, 2) not null,
    paid_amount numeric(19, 2) not null,
    lead_minutes integer not null,
    notes text,
    cancellation_reason text,
    sent_at timestamptz,
    cancelled_at timestamptz,
    forfeited_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    primary key (id),
    constraint fk_pre_orders_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_pre_orders_reservation foreign key (reservation_id) references reservations(id),
    constraint fk_pre_orders_order foreign key (order_id) references orders(id),
    constraint fk_pre_orders_created_by_user foreign key (created_by) references users(id),
    constraint fk_pre_orders_updated_by_user foreign key (updated_by) references users(id),
    check (
        status in ('SCHEDULED', 'SENT', 'CANCELLED', 'FORFEITED')
        and payment_status in ('PAID', 'REFUNDED', 'RETAINED')
        and source in ('ONLINE', 'STAFF')
        and char_length(currency) = 3
        and subtotal >= 0
        and tax_total >= 0
        and service_charge_total >= 0
        and total >= 0
        and paid_amount >= 0
        and lead_minutes >= 0
        and (status not in ('SENT', 'FORFEITED') or (order_id is not null and sent_at is not null))
        and (status <> 'CANCELLED' or cancelled_at is not null)
        and (status <> 'FORFEITED' or forfeited_at is not null)
    )
);
-- One live pre-order per reservation; cancelled and forfeited ones stay as history.
create unique index uk_pre_orders_live_reservation on pre_orders (reservation_id) where status in ('SCHEDULED', 'SENT');
create index idx_pre_orders_restaurant_id on pre_orders (restaurant_id);
create index idx_pre_orders_reservation_id on pre_orders (reservation_id);
create index idx_pre_orders_order_id on pre_orders (order_id);
create index idx_pre_orders_status on pre_orders (status);

create table pre_order_items (
    id uuid not null,
    pre_order_id uuid not null,
    menu_item_id uuid not null,
    variant_id uuid,
    item_name_snapshot varchar(150) not null,
    variant_name_snapshot varchar(120),
    sku_snapshot varchar(80),
    quantity integer not null,
    unit_price_snapshot numeric(19, 2) not null,
    variant_price_delta_snapshot numeric(19, 2) not null,
    price_delta_total numeric(19, 2) not null,
    line_total numeric(19, 2) not null,
    notes text,
    display_order integer not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint fk_pre_order_items_pre_order foreign key (pre_order_id) references pre_orders(id) on delete cascade,
    constraint fk_pre_order_items_menu_item foreign key (menu_item_id) references "menu-items"(id),
    constraint fk_pre_order_items_variant foreign key (variant_id) references "menu-variants"(id),
    check (
        char_length(btrim(item_name_snapshot)) > 0
        and quantity > 0
        and unit_price_snapshot >= 0
        and line_total >= 0
        and display_order >= 0
    )
);
create index idx_pre_order_items_pre_order_id on pre_order_items (pre_order_id);
create index idx_pre_order_items_menu_item_id on pre_order_items (menu_item_id);

create table pre_order_item_options (
    id uuid not null,
    pre_order_item_id uuid not null,
    option_item_id uuid not null,
    option_name_snapshot varchar(150) not null,
    price_delta_snapshot numeric(19, 2) not null,
    quantity integer not null,
    notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint fk_pre_order_item_options_item foreign key (pre_order_item_id) references pre_order_items(id) on delete cascade,
    constraint fk_pre_order_item_options_option_item foreign key (option_item_id) references "option-items"(id),
    check (
        char_length(btrim(option_name_snapshot)) > 0
        and quantity > 0
    )
);
create index idx_pre_order_item_options_item_id on pre_order_item_options (pre_order_item_id);

-- What was paid in advance (e.g. through a pre-order); the order is PAID once this covers its total.
alter table orders add column prepaid_total numeric(19, 2) not null default 0;
alter table orders add constraint ck_orders_prepaid_total check (prepaid_total >= 0);

-- Restaurants opt in to pre-orders and choose how many minutes before a booking the kitchen gets them.
alter table settings add column pre_orders_enabled boolean not null default false;
alter table settings add column pre_order_lead_minutes integer not null default 15;
alter table settings add constraint ck_settings_pre_order_lead_minutes check (pre_order_lead_minutes between 0 and 240);
