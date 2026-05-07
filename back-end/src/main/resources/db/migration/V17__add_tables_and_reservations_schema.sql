create table customers (
    id uuid not null,
    restaurant_id uuid not null,
    code varchar(50),
    first_name varchar(100),
    last_name varchar(100),
    email varchar(150),
    phone varchar(50),
    notes text,
    is_active boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users(id),
    updated_by uuid references users(id),
    deleted_at timestamptz,
    primary key (id),
    constraint uk_customers_restaurant_code unique (restaurant_id, code),
    constraint fk_customers_restaurant foreign key (restaurant_id) references restaurants(id),
    check (
        (code IS NULL OR char_length(btrim(code)) > 0)
        AND (
            first_name IS NOT NULL
            OR last_name IS NOT NULL
            OR email IS NOT NULL
            OR phone IS NOT NULL
        )
    )
);
create index idx_customers_restaurant_id on customers (restaurant_id);
create index idx_customers_email on customers (email);
create index idx_customers_phone on customers (phone);
create index idx_customers_deleted_at on customers (deleted_at);
create index idx_customers_created_by on customers (created_by);
create index idx_customers_updated_by on customers (updated_by);

create table "table-categories" (
    id uuid not null,
    branch_id uuid not null,
    code varchar(50) not null,
    name varchar(100) not null,
    description text,
    default_capacity integer not null,
    location_type varchar(30),
    color varchar(20),
    display_order integer not null,
    is_active boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint uk_table_categories_branch_code unique (branch_id, code),
    constraint uk_table_categories_branch_name unique (branch_id, name),
    constraint fk_table_categories_branch foreign key (branch_id) references branches(id),
    check (
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND default_capacity > 0
        AND display_order >= 0
        AND (
            location_type IS NULL
            OR location_type IN ('INDOOR', 'OUTDOOR', 'PRIVATE_ROOM', 'BAR', 'PATIO', 'ROOFTOP')
        )
    )
);
create index idx_table_categories_branch_id on "table-categories" (branch_id);

create table tables (
    id uuid not null,
    restaurant_id uuid not null,
    branch_id uuid not null,
    category_id uuid,
    table_number varchar(30) not null,
    name varchar(100),
    capacity integer not null,
    floor varchar(50),
    position_x numeric(10, 2),
    position_y numeric(10, 2),
    shape varchar(30) not null,
    status varchar(30) not null,
    is_active boolean not null,
    qr_code_value varchar(255),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users(id),
    updated_by uuid references users(id),
    primary key (id),
    constraint uk_tables_branch_table_number unique (branch_id, table_number),
    constraint fk_tables_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_tables_branch foreign key (branch_id) references branches(id),
    constraint fk_tables_category foreign key (category_id) references "table-categories"(id),
    check (
        char_length(btrim(table_number)) > 0
        AND capacity > 0
        AND (name IS NULL OR char_length(btrim(name)) > 0)
        AND shape IN ('RECTANGLE', 'ROUND', 'SQUARE', 'OVAL', 'CUSTOM')
        AND status IN ('AVAILABLE', 'RESERVED', 'OCCUPIED', 'DIRTY', 'MAINTENANCE', 'OUT_OF_SERVICE')
        AND (
            (position_x IS NULL AND position_y IS NULL)
            OR (position_x IS NOT NULL AND position_y IS NOT NULL)
        )
    )
);
create index idx_tables_restaurant_id on tables (restaurant_id);
create index idx_tables_branch_id on tables (branch_id);
create index idx_tables_category_id on tables (category_id);
create index idx_tables_status on tables (status);
create index idx_tables_created_by on tables (created_by);
create index idx_tables_updated_by on tables (updated_by);

create table reservations (
    id uuid not null,
    restaurant_id uuid not null,
    branch_id uuid not null,
    customer_id uuid,
    reservation_code varchar(50) not null,
    source varchar(30) not null,
    status varchar(30) not null,
    party_size integer not null,
    reservation_start timestamptz not null,
    reservation_end timestamptz not null,
    contact_name varchar(150),
    contact_phone varchar(50),
    contact_email varchar(150),
    seating_preference varchar(50),
    special_requests text,
    internal_notes text,
    deposit_required boolean not null,
    deposit_amount numeric(12, 2),
    deposit_status varchar(30) not null,
    confirmed_at timestamptz,
    cancelled_at timestamptz,
    cancellation_reason text,
    checked_in_at timestamptz,
    seated_at timestamptz,
    completed_at timestamptz,
    no_show_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users(id),
    updated_by uuid references users(id),
    primary key (id),
    constraint uk_reservations_restaurant_code unique (restaurant_id, reservation_code),
    constraint fk_reservations_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_reservations_branch foreign key (branch_id) references branches(id),
    constraint fk_reservations_customer foreign key (customer_id) references customers(id),
    check (
        char_length(btrim(reservation_code)) > 0
        AND source IN ('INTERNAL', 'WEB', 'MOBILE', 'PHONE', 'WALK_IN', 'THIRD_PARTY')
        AND status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'SEATED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')
        AND deposit_status IN ('NOT_REQUIRED', 'PENDING', 'PAID', 'PARTIALLY_PAID', 'REFUNDED', 'FORFEITED', 'WAIVED')
        AND party_size > 0
        AND reservation_end > reservation_start
        AND (
            customer_id IS NOT NULL
            OR contact_name IS NOT NULL
            OR contact_phone IS NOT NULL
            OR contact_email IS NOT NULL
        )
        AND (deposit_amount IS NULL OR deposit_amount >= 0)
        AND (
            deposit_required = true
            OR (deposit_amount IS NULL AND deposit_status = 'NOT_REQUIRED')
        )
        AND (
            deposit_required = false
            OR (deposit_amount IS NOT NULL AND deposit_amount > 0 AND deposit_status <> 'NOT_REQUIRED')
        )
        AND (cancelled_at IS NULL OR status = 'CANCELLED')
        AND (no_show_at IS NULL OR status = 'NO_SHOW')
        AND (completed_at IS NULL OR status = 'COMPLETED')
        AND (
            checked_in_at IS NULL
            OR status IN ('CHECKED_IN', 'SEATED', 'COMPLETED')
        )
        AND (
            seated_at IS NULL
            OR status IN ('SEATED', 'COMPLETED')
        )
        AND (
            confirmed_at IS NULL
            OR status <> 'PENDING'
        )
    )
);
create index idx_reservations_restaurant_id on reservations (restaurant_id);
create index idx_reservations_branch_id on reservations (branch_id);
create index idx_reservations_customer_id on reservations (customer_id);
create index idx_reservations_status on reservations (status);
create index idx_reservations_reservation_start on reservations (reservation_start);
create index idx_reservations_created_by on reservations (created_by);
create index idx_reservations_updated_by on reservations (updated_by);

create table "reservation-status-history" (
    id uuid not null,
    reservation_id uuid not null,
    old_status varchar(30),
    new_status varchar(30) not null,
    reason text,
    changed_by uuid references users(id),
    changed_at timestamptz not null,
    primary key (id),
    constraint fk_reservation_status_history_reservation foreign key (reservation_id) references reservations(id),
    check (
        (old_status IS NULL OR old_status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'SEATED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'))
        AND new_status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'SEATED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')
        AND (old_status IS NULL OR old_status <> new_status)
    )
);
create index idx_reservation_status_history_reservation_id on "reservation-status-history" (reservation_id);
create index idx_reservation_status_history_changed_by on "reservation-status-history" (changed_by);
create index idx_reservation_status_history_changed_at on "reservation-status-history" (changed_at);

create table reservation_tables (
    id uuid not null,
    reservation_id uuid not null,
    table_id uuid not null,
    is_primary boolean not null,
    assigned_at timestamptz not null,
    assigned_by uuid references users(id),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint uk_reservation_tables_reservation_table unique (reservation_id, table_id),
    constraint fk_reservation_tables_reservation foreign key (reservation_id) references reservations(id),
    constraint fk_reservation_tables_table foreign key (table_id) references tables(id),
    check (assigned_at IS NOT NULL)
);
create index idx_reservation_tables_reservation_id on reservation_tables (reservation_id);
create index idx_reservation_tables_table_id on reservation_tables (table_id);
create index idx_reservation_tables_assigned_by on reservation_tables (assigned_by);
