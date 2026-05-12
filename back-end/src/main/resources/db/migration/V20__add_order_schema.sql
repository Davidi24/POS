create table orders (
    id uuid not null,
    restaurant_id uuid not null,
    branch_id uuid not null,
    table_id uuid,
    reservation_id uuid,
    customer_id uuid,
    order_number varchar(50) not null,
    currency char(3) not null,
    order_type varchar(30) not null,
    source varchar(30) not null,
    status varchar(30) not null,
    fulfillment_status varchar(30) not null,
    payment_status varchar(30) not null,
    guest_count integer not null,
    notes text,
    subtotal numeric(19, 2) not null,
    discount_total numeric(19, 2) not null,
    tax_total numeric(19, 2) not null,
    service_charge_total numeric(19, 2) not null,
    total numeric(19, 2) not null,
    opened_at timestamptz not null,
    closed_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users(id),
    updated_by uuid references users(id),
    primary key (id),
    constraint uk_orders_restaurant_order_number unique (restaurant_id, order_number),
    constraint fk_orders_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_orders_branch foreign key (branch_id) references branches(id),
    constraint fk_orders_table foreign key (table_id) references tables(id),
    constraint fk_orders_reservation foreign key (reservation_id) references reservations(id),
    constraint fk_orders_customer foreign key (customer_id) references customers(id),
    check (
        char_length(btrim(order_number)) > 0
        AND char_length(currency) = 3
        AND order_type IN ('DINE_IN', 'TAKEAWAY', 'DELIVERY')
        AND source IN ('POS', 'WEB', 'MOBILE', 'QR_TABLE', 'KIOSK', 'PHONE', 'THIRD_PARTY')
        AND status IN ('DRAFT', 'OPEN', 'CLOSED', 'CANCELLED', 'VOIDED')
        AND fulfillment_status IN (
            'PENDING',
            'IN_PREPARATION',
            'READY',
            'PARTIALLY_FULFILLED',
            'FULFILLED',
            'DELIVERED',
            'CANCELLED'
        )
        AND payment_status IN (
            'UNPAID',
            'PARTIALLY_PAID',
            'PAID',
            'PARTIALLY_REFUNDED',
            'REFUNDED',
            'VOIDED'
        )
        AND guest_count > 0
        AND subtotal >= 0
        AND discount_total >= 0
        AND tax_total >= 0
        AND service_charge_total >= 0
        AND total >= 0
        AND (closed_at IS NULL OR closed_at >= opened_at)
        AND (
            status = 'CLOSED'
            OR closed_at IS NULL
        )
        AND (
            status IN ('CANCELLED', 'VOIDED')
            OR fulfillment_status <> 'CANCELLED'
        )
        AND (
            status NOT IN ('CANCELLED', 'VOIDED')
            OR fulfillment_status = 'CANCELLED'
        )
        AND (
            reservation_id IS NULL
            OR order_type = 'DINE_IN'
        )
    )
);
create index idx_orders_restaurant_id on orders (restaurant_id);
create index idx_orders_branch_id on orders (branch_id);
create index idx_orders_table_id on orders (table_id);
create index idx_orders_reservation_id on orders (reservation_id);
create index idx_orders_customer_id on orders (customer_id);
create index idx_orders_status on orders (status);
create index idx_orders_fulfillment_status on orders (fulfillment_status);
create index idx_orders_payment_status on orders (payment_status);
create index idx_orders_opened_at on orders (opened_at);
create index idx_orders_created_by on orders (created_by);
create index idx_orders_updated_by on orders (updated_by);

create table order_line_items (
    id uuid not null,
    order_id uuid not null,
    menu_item_id uuid not null,
    variant_id uuid,
    item_name_snapshot varchar(150) not null,
    variant_name_snapshot varchar(120),
    sku_snapshot varchar(80),
    quantity integer not null,
    unit_price_snapshot numeric(19, 2) not null,
    price_delta_total numeric(19, 2) not null,
    discount_total numeric(19, 2) not null,
    tax_total numeric(19, 2) not null,
    line_total numeric(19, 2) not null,
    status varchar(30) not null,
    notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint fk_order_line_items_order foreign key (order_id) references orders(id),
    constraint fk_order_line_items_menu_item foreign key (menu_item_id) references "menu-items"(id),
    constraint fk_order_line_items_variant foreign key (variant_id) references "menu-variants"(id),
    check (
        char_length(btrim(item_name_snapshot)) > 0
        AND (variant_name_snapshot IS NULL OR char_length(btrim(variant_name_snapshot)) > 0)
        AND (sku_snapshot IS NULL OR char_length(btrim(sku_snapshot)) > 0)
        AND quantity > 0
        AND unit_price_snapshot >= 0
        AND discount_total >= 0
        AND tax_total >= 0
        AND line_total >= 0
        AND status IN ('PENDING', 'FIRED', 'PREPARING', 'READY', 'FULFILLED', 'CANCELLED', 'VOIDED')
    )
);
create index idx_order_line_items_order_id on order_line_items (order_id);
create index idx_order_line_items_menu_item_id on order_line_items (menu_item_id);
create index idx_order_line_items_variant_id on order_line_items (variant_id);
create index idx_order_line_items_status on order_line_items (status);

create table order_item_options (
    id uuid not null,
    order_line_item_id uuid not null,
    option_item_id uuid not null,
    option_name_snapshot varchar(150) not null,
    price_delta_snapshot numeric(19, 2) not null,
    quantity integer not null,
    notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint uk_order_item_options_line_option unique (order_line_item_id, option_item_id),
    constraint fk_order_item_options_order_line_item foreign key (order_line_item_id) references order_line_items(id),
    constraint fk_order_item_options_option_item foreign key (option_item_id) references "option-items"(id),
    check (
        char_length(btrim(option_name_snapshot)) > 0
        AND quantity > 0
    )
);
create index idx_order_item_options_order_line_item_id on order_item_options (order_line_item_id);
create index idx_order_item_options_option_item_id on order_item_options (option_item_id);

create table order_discounts (
    id uuid not null,
    order_id uuid not null,
    name varchar(100) not null,
    discount_type varchar(30) not null,
    discount_value numeric(19, 2) not null,
    amount_applied numeric(19, 2) not null,
    reason text,
    applied_by uuid references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint fk_order_discounts_order foreign key (order_id) references orders(id),
    check (
        char_length(btrim(name)) > 0
        AND discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT', 'PROMOTION', 'LOYALTY', 'MANUAL', 'COMP')
        AND discount_value >= 0
        AND amount_applied >= 0
        AND (
            discount_type <> 'PERCENTAGE'
            OR discount_value <= 100
        )
    )
);
create index idx_order_discounts_order_id on order_discounts (order_id);
create index idx_order_discounts_applied_by on order_discounts (applied_by);

create table order_events (
    id uuid not null,
    order_id uuid not null,
    event_type varchar(30) not null,
    note text,
    created_by uuid references users(id),
    created_at timestamptz not null,
    primary key (id),
    constraint fk_order_events_order foreign key (order_id) references orders(id),
    check (
        event_type IN (
            'CREATED',
            'UPDATED',
            'STATUS_UPDATED',
            'FULFILLMENT_UPDATED',
            'PAYMENT_UPDATED',
            'ITEM_ADDED',
            'ITEM_UPDATED',
            'ITEM_REMOVED',
            'ITEM_VOIDED',
            'DISCOUNT_APPLIED',
            'DISCOUNT_REMOVED',
            'SENT_TO_KITCHEN',
            'READY',
            'FULFILLED',
            'TABLE_CHANGED',
            'RESERVATION_LINKED',
            'REOPENED',
            'CLOSED',
            'CANCELLED',
            'VOIDED',
            'NOTE_ADDED'
        )
    )
);
create index idx_order_events_order_id on order_events (order_id);
create index idx_order_events_created_by on order_events (created_by);
create index idx_order_events_created_at on order_events (created_at);
create index idx_order_events_event_type on order_events (event_type);
