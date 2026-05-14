create table kds_stations (
    id uuid not null,
    restaurant_id uuid not null,
    branch_id uuid not null,
    device_id uuid,
    code varchar(80) not null,
    name varchar(150) not null,
    station_type varchar(30) not null,
    display_order integer not null,
    is_active boolean not null,
    accepts_scheduled_orders boolean not null,
    screen_label varchar(80),
    notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    primary key (id),
    constraint uk_kds_stations_branch_code unique (branch_id, code),
    constraint uk_kds_stations_device_id unique (device_id),
    constraint fk_kds_stations_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_kds_stations_branch foreign key (branch_id) references branches(id),
    constraint fk_kds_stations_device foreign key (device_id) references devices(id),
    constraint fk_kds_stations_created_by_user foreign key (created_by) references users(id),
    constraint fk_kds_stations_updated_by_user foreign key (updated_by) references users(id),
    check (
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND station_type IN ('PREP', 'GRILL', 'FRY', 'GARDE_MANGER', 'BAR', 'DESSERT', 'EXPO', 'PACKING')
        AND display_order >= 0
    )
);
create index idx_kds_stations_restaurant_id on kds_stations (restaurant_id);
create index idx_kds_stations_branch_id on kds_stations (branch_id);
create index idx_kds_stations_device_id on kds_stations (device_id);
create index idx_kds_stations_station_type on kds_stations (station_type);
create index idx_kds_stations_created_by on kds_stations (created_by);
create index idx_kds_stations_updated_by on kds_stations (updated_by);

create table kds_station_routings (
    id uuid not null,
    station_id uuid not null,
    menu_item_id uuid not null,
    display_order integer not null,
    priority varchar(30) not null,
    course_label varchar(50),
    is_active boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint uk_kds_station_routings_station_menu_item unique (station_id, menu_item_id),
    constraint fk_kds_station_routings_station foreign key (station_id) references kds_stations(id),
    constraint fk_kds_station_routings_menu_item foreign key (menu_item_id) references "menu-items"(id),
    check (
        priority IN ('NORMAL', 'RUSH', 'VIP', 'HOLD_FIRE')
        AND display_order >= 0
    )
);
create index idx_kds_station_routings_station_id on kds_station_routings (station_id);
create index idx_kds_station_routings_menu_item_id on kds_station_routings (menu_item_id);

create table kds_tickets (
    id uuid not null,
    restaurant_id uuid not null,
    branch_id uuid not null,
    station_id uuid not null,
    order_id uuid not null,
    ticket_number varchar(50) not null,
    status varchar(30) not null,
    priority varchar(30) not null,
    course_name varchar(50),
    notes text,
    void_reason text,
    fired_at timestamptz,
    started_at timestamptz,
    ready_at timestamptz,
    completed_at timestamptz,
    due_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    primary key (id),
    constraint uk_kds_tickets_restaurant_ticket_number unique (restaurant_id, ticket_number),
    constraint fk_kds_tickets_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_kds_tickets_branch foreign key (branch_id) references branches(id),
    constraint fk_kds_tickets_station foreign key (station_id) references kds_stations(id),
    constraint fk_kds_tickets_order foreign key (order_id) references orders(id),
    constraint fk_kds_tickets_created_by_user foreign key (created_by) references users(id),
    constraint fk_kds_tickets_updated_by_user foreign key (updated_by) references users(id),
    check (
        char_length(btrim(ticket_number)) > 0
        AND status IN ('PENDING', 'FIRED', 'IN_PROGRESS', 'READY', 'EXPO_READY', 'COMPLETED', 'CANCELLED')
        AND priority IN ('NORMAL', 'RUSH', 'VIP', 'HOLD_FIRE')
        AND (
            completed_at IS NULL
            OR ready_at IS NOT NULL
        )
        AND (
            ready_at IS NULL
            OR fired_at IS NOT NULL
        )
    )
);
create index idx_kds_tickets_restaurant_id on kds_tickets (restaurant_id);
create index idx_kds_tickets_branch_id on kds_tickets (branch_id);
create index idx_kds_tickets_station_id on kds_tickets (station_id);
create index idx_kds_tickets_order_id on kds_tickets (order_id);
create index idx_kds_tickets_status on kds_tickets (status);
create index idx_kds_tickets_due_at on kds_tickets (due_at);
create index idx_kds_tickets_created_by on kds_tickets (created_by);
create index idx_kds_tickets_updated_by on kds_tickets (updated_by);

create table kds_ticket_items (
    id uuid not null,
    kds_ticket_id uuid not null,
    order_line_item_id uuid not null,
    item_name_snapshot varchar(150) not null,
    quantity integer not null,
    status varchar(30) not null,
    priority varchar(30) not null,
    seat_label varchar(30),
    notes text,
    fired_at timestamptz,
    ready_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint uk_kds_ticket_items_ticket_order_line_item unique (kds_ticket_id, order_line_item_id),
    constraint fk_kds_ticket_items_kds_ticket foreign key (kds_ticket_id) references kds_tickets(id),
    constraint fk_kds_ticket_items_order_line_item foreign key (order_line_item_id) references order_line_items(id),
    check (
        char_length(btrim(item_name_snapshot)) > 0
        AND quantity > 0
        AND status IN ('PENDING', 'FIRED', 'IN_PROGRESS', 'READY', 'EXPO_READY', 'COMPLETED', 'CANCELLED')
        AND priority IN ('NORMAL', 'RUSH', 'VIP', 'HOLD_FIRE')
        AND (
            completed_at IS NULL
            OR ready_at IS NOT NULL
        )
        AND (
            ready_at IS NULL
            OR fired_at IS NOT NULL
        )
    )
);
create index idx_kds_ticket_items_kds_ticket_id on kds_ticket_items (kds_ticket_id);
create index idx_kds_ticket_items_order_line_item_id on kds_ticket_items (order_line_item_id);
create index idx_kds_ticket_items_status on kds_ticket_items (status);
