create table settings (
    id uuid not null,
    restaurant_id uuid not null,
    default_branch_id uuid,
    default_language varchar(20) not null,
    date_format varchar(30) not null,
    time_format varchar(30) not null,
    week_start_day varchar(15) not null check (week_start_day in ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    order_sequence_prefix varchar(20),
    invoice_sequence_prefix varchar(20),
    reservation_slot_minutes integer not null,
    default_table_turn_time_minutes integer not null,
    service_charge_enabled boolean not null,
    service_charge_type varchar(20) check (service_charge_type in ('FIXED_AMOUNT', 'PERCENTAGE')),
    service_charge_value numeric(12, 2),
    cash_rounding_enabled boolean not null,
    cash_rounding_increment numeric(12, 2),
    allow_split_bills boolean not null,
    allow_open_tickets boolean not null,
    require_customer_for_invoice boolean not null,
    enable_qr_ordering boolean not null,
    enable_takeaway boolean not null,
    enable_delivery boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users(id),
    updated_by uuid references users(id),
    primary key (id),
    constraint uk_settings_restaurant_id unique (restaurant_id),
    constraint fk_settings_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_settings_default_branch foreign key (default_branch_id) references branches(id),
    check (
        char_length(btrim(default_language)) > 0
        AND char_length(btrim(date_format)) > 0
        AND char_length(btrim(time_format)) > 0
        AND (order_sequence_prefix IS NULL OR char_length(btrim(order_sequence_prefix)) > 0)
        AND (invoice_sequence_prefix IS NULL OR char_length(btrim(invoice_sequence_prefix)) > 0)
        AND reservation_slot_minutes > 0
        AND default_table_turn_time_minutes > 0
        AND (service_charge_value IS NULL OR service_charge_value >= 0)
        AND (cash_rounding_increment IS NULL OR cash_rounding_increment > 0)
        AND (service_charge_enabled = false OR (service_charge_type IS NOT NULL AND service_charge_value IS NOT NULL))
    )
);
create index idx_settings_default_branch_id on settings (default_branch_id);
create index idx_settings_created_by on settings (created_by);
create index idx_settings_updated_by on settings (updated_by);

create table "settings-receipts" (
    id uuid not null,
    settings_id uuid not null,
    auto_print_customer_receipt boolean not null,
    auto_print_kitchen_ticket boolean not null,
    receipt_copies integer not null,
    show_logo boolean not null,
    show_tax_breakdown boolean not null,
    show_server_name boolean not null,
    show_table_name boolean not null,
    show_order_number boolean not null,
    show_qr_code boolean not null,
    print_voided_items boolean not null,
    footer_note text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint uk_settings_receipts_settings_id unique (settings_id),
    constraint fk_settings_receipts_settings foreign key (settings_id) references settings(id),
    check (receipt_copies > 0)
);

create table "settings-order-rules" (
    id uuid not null,
    settings_id uuid not null,
    auto_fire_to_kitchen boolean not null,
    allow_item_void boolean not null,
    allow_discount_without_manager boolean not null,
    allow_backdated_orders boolean not null,
    require_reason_for_void boolean not null,
    require_reason_for_discount boolean not null,
    merge_orders_enabled boolean not null,
    transfer_orders_enabled boolean not null,
    reopen_closed_orders_enabled boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint uk_settings_order_rules_settings_id unique (settings_id),
    constraint fk_settings_order_rules_settings foreign key (settings_id) references settings(id)
);

create table "settings-reservation-rules" (
    id uuid not null,
    settings_id uuid not null,
    branch_id uuid,
    rule_name varchar(120) not null,
    priority integer not null,
    is_active boolean not null,
    effective_from timestamptz,
    effective_to timestamptz,
    advance_booking_days integer not null,
    min_party_size integer not null,
    max_party_size integer not null,
    default_duration_minutes integer not null,
    buffer_minutes integer not null,
    allow_online_reservations boolean not null,
    require_deposit boolean not null,
    deposit_type varchar(20) check (deposit_type in ('FIXED_AMOUNT', 'PERCENTAGE')),
    deposit_value numeric(12, 2),
    auto_confirm_reservations boolean not null,
    cancellation_window_hours integer not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint fk_settings_reservation_rules_settings foreign key (settings_id) references settings(id),
    constraint fk_settings_reservation_rules_branch foreign key (branch_id) references branches(id),
    check (
        char_length(btrim(rule_name)) > 0
        AND priority >= 0
        AND advance_booking_days >= 0
        AND min_party_size > 0
        AND max_party_size >= min_party_size
        AND default_duration_minutes > 0
        AND buffer_minutes >= 0
        AND cancellation_window_hours >= 0
        AND (deposit_value IS NULL OR deposit_value >= 0)
        AND (effective_from IS NULL OR effective_to IS NULL OR effective_to > effective_from)
        AND (require_deposit = false OR (deposit_type IS NOT NULL AND deposit_value IS NOT NULL))
    )
);
create index idx_settings_reservation_rules_settings_id on "settings-reservation-rules" (settings_id);
create index idx_settings_reservation_rules_branch_id on "settings-reservation-rules" (branch_id);
create index idx_settings_reservation_rules_active_priority on "settings-reservation-rules" (is_active, priority);

create table "settings-business-hours" (
    id uuid not null,
    branch_id uuid not null,
    day_of_week integer not null,
    open_time time,
    close_time time,
    is_closed boolean not null,
    is_overnight boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint uk_settings_business_hours_branch_day unique (branch_id, day_of_week),
    constraint fk_settings_business_hours_branch foreign key (branch_id) references branches(id),
    check (
        day_of_week between 1 and 7
        AND (
            is_closed = true
            OR (open_time IS NOT NULL AND close_time IS NOT NULL AND open_time <> close_time)
        )
    )
);
create index idx_settings_business_hours_branch_id on "settings-business-hours" (branch_id);

create table "settings-special-hours" (
    id uuid not null,
    branch_id uuid not null,
    special_date date not null,
    open_time time,
    close_time time,
    is_closed boolean not null,
    note varchar(255),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint uk_settings_special_hours_branch_date unique (branch_id, special_date),
    constraint fk_settings_special_hours_branch foreign key (branch_id) references branches(id),
    check (
        note IS NULL OR char_length(note) <= 255
    ),
    check (
        is_closed = true
        OR (open_time IS NOT NULL AND close_time IS NOT NULL AND close_time > open_time)
    )
);
create index idx_settings_special_hours_branch_id on "settings-special-hours" (branch_id);
