create table inventory_items (
    id uuid not null,
    restaurant_id uuid not null,
    code varchar(80) not null,
    name varchar(150) not null,
    description text,
    item_type varchar(30) not null,
    base_unit varchar(30) not null,
    barcode varchar(80),
    supplier_name varchar(150),
    supplier_sku varchar(100),
    cost_per_unit numeric(19, 4) not null,
    reorder_point numeric(12, 3),
    par_level numeric(12, 3),
    track_inventory boolean not null,
    is_active boolean not null,
    storage_notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    deleted_at timestamptz,
    primary key (id),
    constraint uk_inventory_items_restaurant_code unique (restaurant_id, code),
    constraint fk_inventory_items_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_inventory_items_created_by_user foreign key (created_by) references users(id),
    constraint fk_inventory_items_updated_by_user foreign key (updated_by) references users(id),
    check (
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND item_type IN (
            'INGREDIENT',
            'PREPARED_COMPONENT',
            'FINISHED_GOOD',
            'BEVERAGE',
            'ALCOHOL',
            'PACKAGING',
            'SUPPLY'
        )
        AND base_unit IN (
            'EACH',
            'GRAM',
            'KILOGRAM',
            'MILLILITER',
            'LITER',
            'OUNCE',
            'POUND',
            'CUP',
            'TABLESPOON',
            'TEASPOON',
            'PORTION',
            'CASE',
            'BOTTLE',
            'PACK',
            'TRAY'
        )
        AND cost_per_unit >= 0
        AND (reorder_point IS NULL OR reorder_point >= 0)
        AND (par_level IS NULL OR par_level >= 0)
    )
);
create index idx_inventory_items_restaurant_id on inventory_items (restaurant_id);
create index idx_inventory_items_item_type on inventory_items (item_type);
create index idx_inventory_items_base_unit on inventory_items (base_unit);
create index idx_inventory_items_deleted_at on inventory_items (deleted_at);
create index idx_inventory_items_created_by on inventory_items (created_by);
create index idx_inventory_items_updated_by on inventory_items (updated_by);

create table inventory_locations (
    id uuid not null,
    restaurant_id uuid not null,
    branch_id uuid,
    code varchar(80) not null,
    name varchar(150) not null,
    location_type varchar(30) not null,
    notes text,
    is_active boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    primary key (id),
    constraint uk_inventory_locations_restaurant_code unique (restaurant_id, code),
    constraint fk_inventory_locations_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_inventory_locations_branch foreign key (branch_id) references branches(id),
    constraint fk_inventory_locations_created_by_user foreign key (created_by) references users(id),
    constraint fk_inventory_locations_updated_by_user foreign key (updated_by) references users(id),
    check (
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND location_type IN (
            'BRANCH_STORAGE',
            'KITCHEN',
            'BAR',
            'WALK_IN',
            'FREEZER',
            'DRY_STORAGE',
            'CENTRAL_WAREHOUSE',
            'TRANSIT'
        )
    )
);
create index idx_inventory_locations_restaurant_id on inventory_locations (restaurant_id);
create index idx_inventory_locations_branch_id on inventory_locations (branch_id);
create index idx_inventory_locations_location_type on inventory_locations (location_type);
create index idx_inventory_locations_created_by on inventory_locations (created_by);
create index idx_inventory_locations_updated_by on inventory_locations (updated_by);

create table inventory_levels (
    id uuid not null,
    location_id uuid not null,
    inventory_item_id uuid not null,
    on_hand_quantity numeric(12, 3) not null,
    committed_quantity numeric(12, 3) not null,
    par_quantity numeric(12, 3),
    reorder_quantity numeric(12, 3),
    last_counted_at timestamptz,
    last_movement_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint uk_inventory_levels_location_item unique (location_id, inventory_item_id),
    constraint fk_inventory_levels_location foreign key (location_id) references inventory_locations(id),
    constraint fk_inventory_levels_inventory_item foreign key (inventory_item_id) references inventory_items(id),
    check (
        on_hand_quantity >= 0
        AND committed_quantity >= 0
        AND (par_quantity IS NULL OR par_quantity >= 0)
        AND (reorder_quantity IS NULL OR reorder_quantity >= 0)
    )
);
create index idx_inventory_levels_location_id on inventory_levels (location_id);
create index idx_inventory_levels_inventory_item_id on inventory_levels (inventory_item_id);
create index idx_inventory_levels_last_counted_at on inventory_levels (last_counted_at);
create index idx_inventory_levels_last_movement_at on inventory_levels (last_movement_at);

create table inventory_counts (
    id uuid not null,
    restaurant_id uuid not null,
    branch_id uuid,
    location_id uuid not null,
    count_number varchar(50) not null,
    status varchar(30) not null,
    scheduled_at timestamptz,
    completed_at timestamptz,
    approved_by uuid,
    approved_at timestamptz,
    variance_value numeric(19, 2) not null,
    notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    primary key (id),
    constraint uk_inventory_counts_restaurant_count_number unique (restaurant_id, count_number),
    constraint fk_inventory_counts_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_inventory_counts_branch foreign key (branch_id) references branches(id),
    constraint fk_inventory_counts_location foreign key (location_id) references inventory_locations(id),
    constraint fk_inventory_counts_approved_by_user foreign key (approved_by) references users(id),
    constraint fk_inventory_counts_created_by_user foreign key (created_by) references users(id),
    constraint fk_inventory_counts_updated_by_user foreign key (updated_by) references users(id),
    check (
        char_length(btrim(count_number)) > 0
        AND status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED', 'APPROVED', 'CANCELLED')
        AND variance_value >= 0
        AND (
            approved_at IS NULL
            OR completed_at IS NOT NULL
        )
    )
);
create index idx_inventory_counts_restaurant_id on inventory_counts (restaurant_id);
create index idx_inventory_counts_branch_id on inventory_counts (branch_id);
create index idx_inventory_counts_location_id on inventory_counts (location_id);
create index idx_inventory_counts_status on inventory_counts (status);
create index idx_inventory_counts_scheduled_at on inventory_counts (scheduled_at);
create index idx_inventory_counts_approved_by on inventory_counts (approved_by);
create index idx_inventory_counts_created_by on inventory_counts (created_by);
create index idx_inventory_counts_updated_by on inventory_counts (updated_by);

create table inventory_count_lines (
    id uuid not null,
    inventory_count_id uuid not null,
    inventory_item_id uuid not null,
    item_name_snapshot varchar(150) not null,
    expected_quantity numeric(12, 3) not null,
    counted_quantity numeric(12, 3) not null,
    variance_quantity numeric(12, 3) not null,
    unit varchar(30) not null,
    unit_cost_snapshot numeric(19, 4) not null,
    variance_value numeric(19, 2) not null,
    notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint uk_inventory_count_lines_count_item unique (inventory_count_id, inventory_item_id),
    constraint fk_inventory_count_lines_inventory_count foreign key (inventory_count_id) references inventory_counts(id),
    constraint fk_inventory_count_lines_inventory_item foreign key (inventory_item_id) references inventory_items(id),
    check (
        char_length(btrim(item_name_snapshot)) > 0
        AND expected_quantity >= 0
        AND counted_quantity >= 0
        AND unit IN (
            'EACH',
            'GRAM',
            'KILOGRAM',
            'MILLILITER',
            'LITER',
            'OUNCE',
            'POUND',
            'CUP',
            'TABLESPOON',
            'TEASPOON',
            'PORTION',
            'CASE',
            'BOTTLE',
            'PACK',
            'TRAY'
        )
        AND unit_cost_snapshot >= 0
        AND variance_value >= 0
    )
);
create index idx_inventory_count_lines_inventory_count_id on inventory_count_lines (inventory_count_id);
create index idx_inventory_count_lines_inventory_item_id on inventory_count_lines (inventory_item_id);

create table inventory_movements (
    id uuid not null,
    location_id uuid not null,
    inventory_item_id uuid not null,
    order_line_item_id uuid,
    movement_type varchar(30) not null,
    quantity_delta numeric(12, 3) not null,
    unit varchar(30) not null,
    unit_cost_snapshot numeric(19, 4) not null,
    total_cost_delta numeric(19, 4) not null,
    reason text,
    reference_type varchar(50),
    reference_id uuid,
    occurred_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    primary key (id),
    constraint fk_inventory_movements_location foreign key (location_id) references inventory_locations(id),
    constraint fk_inventory_movements_inventory_item foreign key (inventory_item_id) references inventory_items(id),
    constraint fk_inventory_movements_order_line_item foreign key (order_line_item_id) references order_line_items(id),
    constraint fk_inventory_movements_created_by_user foreign key (created_by) references users(id),
    constraint fk_inventory_movements_updated_by_user foreign key (updated_by) references users(id),
    check (
        movement_type IN (
            'PURCHASE',
            'RECEIPT',
            'COUNT_ADJUSTMENT',
            'WASTE',
            'TRANSFER_IN',
            'TRANSFER_OUT',
            'SALE_CONSUMPTION',
            'RETURN',
            'VOID',
            'MANUAL_ADJUSTMENT',
            'PREP_PRODUCTION'
        )
        AND unit IN (
            'EACH',
            'GRAM',
            'KILOGRAM',
            'MILLILITER',
            'LITER',
            'OUNCE',
            'POUND',
            'CUP',
            'TABLESPOON',
            'TEASPOON',
            'PORTION',
            'CASE',
            'BOTTLE',
            'PACK',
            'TRAY'
        )
        AND quantity_delta <> 0
        AND unit_cost_snapshot >= 0
        AND occurred_at IS NOT NULL
    )
);
create index idx_inventory_movements_location_id on inventory_movements (location_id);
create index idx_inventory_movements_inventory_item_id on inventory_movements (inventory_item_id);
create index idx_inventory_movements_order_line_item_id on inventory_movements (order_line_item_id);
create index idx_inventory_movements_movement_type on inventory_movements (movement_type);
create index idx_inventory_movements_occurred_at on inventory_movements (occurred_at);
create index idx_inventory_movements_created_by on inventory_movements (created_by);
create index idx_inventory_movements_updated_by on inventory_movements (updated_by);

create table recipes (
    id uuid not null,
    restaurant_id uuid not null,
    menu_item_id uuid,
    code varchar(80) not null,
    name varchar(150) not null,
    description text,
    recipe_type varchar(30) not null,
    status varchar(30) not null,
    version integer not null,
    yield_quantity numeric(12, 3) not null,
    yield_unit varchar(30) not null,
    prep_time_minutes integer,
    cook_time_minutes integer,
    instructions text,
    theoretical_cost numeric(19, 2) not null,
    effective_from timestamptz,
    retired_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    primary key (id),
    constraint uk_recipes_restaurant_code unique (restaurant_id, code),
    constraint uk_recipes_menu_item_version unique (menu_item_id, version),
    constraint fk_recipes_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_recipes_menu_item foreign key (menu_item_id) references "menu-items"(id),
    constraint fk_recipes_created_by_user foreign key (created_by) references users(id),
    constraint fk_recipes_updated_by_user foreign key (updated_by) references users(id),
    check (
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND recipe_type IN ('FINISHED_DISH', 'PREP_BATCH', 'SUB_RECIPE')
        AND status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')
        AND version > 0
        AND yield_quantity > 0
        AND yield_unit IN (
            'EACH',
            'GRAM',
            'KILOGRAM',
            'MILLILITER',
            'LITER',
            'OUNCE',
            'POUND',
            'CUP',
            'TABLESPOON',
            'TEASPOON',
            'PORTION',
            'CASE',
            'BOTTLE',
            'PACK',
            'TRAY'
        )
        AND (prep_time_minutes IS NULL OR prep_time_minutes >= 0)
        AND (cook_time_minutes IS NULL OR cook_time_minutes >= 0)
        AND theoretical_cost >= 0
        AND (
            menu_item_id IS NOT NULL
            OR recipe_type <> 'FINISHED_DISH'
        )
        AND (
            retired_at IS NULL
            OR effective_from IS NULL
            OR retired_at >= effective_from
        )
    )
);
create index idx_recipes_restaurant_id on recipes (restaurant_id);
create index idx_recipes_menu_item_id on recipes (menu_item_id);
create index idx_recipes_recipe_type on recipes (recipe_type);
create index idx_recipes_status on recipes (status);
create index idx_recipes_created_by on recipes (created_by);
create index idx_recipes_updated_by on recipes (updated_by);

create table recipe_components (
    id uuid not null,
    recipe_id uuid not null,
    inventory_item_id uuid,
    child_recipe_id uuid,
    component_type varchar(30) not null,
    component_name_snapshot varchar(150) not null,
    quantity numeric(12, 3) not null,
    unit varchar(30) not null,
    yield_loss_percent numeric(5, 2) not null,
    optional_component boolean not null,
    display_order integer not null,
    notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint fk_recipe_components_recipe foreign key (recipe_id) references recipes(id),
    constraint fk_recipe_components_inventory_item foreign key (inventory_item_id) references inventory_items(id),
    constraint fk_recipe_components_child_recipe foreign key (child_recipe_id) references recipes(id),
    check (
        component_type IN ('INVENTORY_ITEM', 'SUB_RECIPE')
        AND char_length(btrim(component_name_snapshot)) > 0
        AND quantity > 0
        AND unit IN (
            'EACH',
            'GRAM',
            'KILOGRAM',
            'MILLILITER',
            'LITER',
            'OUNCE',
            'POUND',
            'CUP',
            'TABLESPOON',
            'TEASPOON',
            'PORTION',
            'CASE',
            'BOTTLE',
            'PACK',
            'TRAY'
        )
        AND yield_loss_percent >= 0
        AND yield_loss_percent <= 100
        AND display_order >= 0
        AND (
            (component_type = 'INVENTORY_ITEM' AND inventory_item_id IS NOT NULL AND child_recipe_id IS NULL)
            OR (component_type = 'SUB_RECIPE' AND child_recipe_id IS NOT NULL AND inventory_item_id IS NULL)
        )
        AND (
            child_recipe_id IS NULL
            OR child_recipe_id <> recipe_id
        )
    )
);
create index idx_recipe_components_recipe_id on recipe_components (recipe_id);
create index idx_recipe_components_inventory_item_id on recipe_components (inventory_item_id);
create index idx_recipe_components_child_recipe_id on recipe_components (child_recipe_id);
create index idx_recipe_components_component_type on recipe_components (component_type);

create table shifts (
    id uuid not null,
    restaurant_id uuid not null,
    branch_id uuid not null,
    user_id uuid not null,
    device_id uuid,
    status varchar(30) not null,
    scheduled_start timestamptz,
    scheduled_end timestamptz,
    started_at timestamptz not null,
    ended_at timestamptz,
    hourly_rate numeric(12, 2),
    overtime_rate numeric(12, 2),
    regular_minutes integer not null,
    overtime_minutes integer not null,
    declared_cash_tips numeric(19, 2) not null,
    declared_card_tips numeric(19, 2) not null,
    sales_total numeric(19, 2) not null,
    cash_sales_total numeric(19, 2) not null,
    card_sales_total numeric(19, 2) not null,
    opening_drawer_amount numeric(19, 2) not null,
    expected_drawer_amount numeric(19, 2) not null,
    actual_drawer_amount numeric(19, 2),
    notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    primary key (id),
    constraint fk_shifts_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_shifts_branch foreign key (branch_id) references branches(id),
    constraint fk_shifts_user foreign key (user_id) references users(id),
    constraint fk_shifts_device foreign key (device_id) references devices(id),
    constraint fk_shifts_created_by_user foreign key (created_by) references users(id),
    constraint fk_shifts_updated_by_user foreign key (updated_by) references users(id),
    check (
        status IN ('SCHEDULED', 'OPEN', 'ON_BREAK', 'CLOSED', 'MISSED', 'CANCELLED')
        AND regular_minutes >= 0
        AND overtime_minutes >= 0
        AND (hourly_rate IS NULL OR hourly_rate >= 0)
        AND (overtime_rate IS NULL OR overtime_rate >= 0)
        AND declared_cash_tips >= 0
        AND declared_card_tips >= 0
        AND sales_total >= 0
        AND cash_sales_total >= 0
        AND card_sales_total >= 0
        AND opening_drawer_amount >= 0
        AND expected_drawer_amount >= 0
        AND (actual_drawer_amount IS NULL OR actual_drawer_amount >= 0)
        AND (ended_at IS NULL OR started_at IS NOT NULL)
        AND (ended_at IS NULL OR ended_at >= started_at)
    )
);
create index idx_shifts_restaurant_id on shifts (restaurant_id);
create index idx_shifts_branch_id on shifts (branch_id);
create index idx_shifts_user_id on shifts (user_id);
create index idx_shifts_device_id on shifts (device_id);
create index idx_shifts_status on shifts (status);
create index idx_shifts_started_at on shifts (started_at);
create index idx_shifts_created_by on shifts (created_by);
create index idx_shifts_updated_by on shifts (updated_by);

create table shift_breaks (
    id uuid not null,
    shift_id uuid not null,
    break_type varchar(30) not null,
    is_paid boolean not null,
    started_at timestamptz not null,
    ended_at timestamptz,
    notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint fk_shift_breaks_shift foreign key (shift_id) references shifts(id),
    check (
        break_type IN ('REST', 'MEAL', 'PAID_BREAK', 'UNPAID_BREAK')
        AND started_at IS NOT NULL
        AND (ended_at IS NULL OR ended_at >= started_at)
    )
);
create index idx_shift_breaks_shift_id on shift_breaks (shift_id);
create index idx_shift_breaks_break_type on shift_breaks (break_type);
create index idx_shift_breaks_started_at on shift_breaks (started_at);

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
    )
);
create index idx_kds_ticket_items_kds_ticket_id on kds_ticket_items (kds_ticket_id);
create index idx_kds_ticket_items_order_line_item_id on kds_ticket_items (order_line_item_id);
create index idx_kds_ticket_items_status on kds_ticket_items (status);

create table payments (
    id uuid not null,
    restaurant_id uuid not null,
    branch_id uuid not null,
    order_id uuid not null,
    shift_id uuid,
    customer_id uuid,
    reference_number varchar(50) not null,
    method varchar(30) not null,
    status varchar(30) not null,
    amount numeric(19, 2) not null,
    tip_amount numeric(19, 2) not null,
    surcharge_amount numeric(19, 2) not null,
    refunded_amount numeric(19, 2) not null,
    currency varchar(3) not null,
    external_reference varchar(100),
    gateway_name varchar(100),
    card_brand varchar(40),
    card_last4 varchar(4),
    receipt_number varchar(50),
    paid_at timestamptz not null,
    notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    primary key (id),
    constraint uk_payments_order_reference unique (order_id, reference_number),
    constraint fk_payments_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_payments_branch foreign key (branch_id) references branches(id),
    constraint fk_payments_order foreign key (order_id) references orders(id),
    constraint fk_payments_shift foreign key (shift_id) references shifts(id),
    constraint fk_payments_customer foreign key (customer_id) references customers(id),
    constraint fk_payments_created_by_user foreign key (created_by) references users(id),
    constraint fk_payments_updated_by_user foreign key (updated_by) references users(id),
    check (
        char_length(btrim(reference_number)) > 0
        AND char_length(currency) = 3
        AND method IN (
            'CASH',
            'CARD',
            'CONTACTLESS',
            'DIGITAL_WALLET',
            'GIFT_CARD',
            'HOUSE_ACCOUNT',
            'LOYALTY',
            'BANK_TRANSFER',
            'OTHER'
        )
        AND status IN (
            'PENDING',
            'AUTHORIZED',
            'CAPTURED',
            'PARTIALLY_REFUNDED',
            'REFUNDED',
            'FAILED',
            'VOIDED'
        )
        AND amount > 0
        AND tip_amount >= 0
        AND surcharge_amount >= 0
        AND refunded_amount >= 0
        AND refunded_amount <= (amount + tip_amount + surcharge_amount)
    )
);
create index idx_payments_restaurant_id on payments (restaurant_id);
create index idx_payments_branch_id on payments (branch_id);
create index idx_payments_order_id on payments (order_id);
create index idx_payments_shift_id on payments (shift_id);
create index idx_payments_customer_id on payments (customer_id);
create index idx_payments_method on payments (method);
create index idx_payments_status on payments (status);
create index idx_payments_paid_at on payments (paid_at);
create index idx_payments_created_by on payments (created_by);
create index idx_payments_updated_by on payments (updated_by);

create table payment_transactions (
    id uuid not null,
    payment_id uuid not null,
    transaction_type varchar(30) not null,
    status varchar(30) not null,
    gateway_transaction_id varchar(100),
    processor_reference varchar(100),
    amount numeric(19, 2) not null,
    currency varchar(3) not null,
    response_code varchar(40),
    response_message varchar(255),
    payload text,
    processed_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint fk_payment_transactions_payment foreign key (payment_id) references payments(id),
    check (
        transaction_type IN ('AUTHORIZATION', 'CAPTURE', 'SALE', 'TIP_ADJUST', 'REFUND', 'VOID', 'REVERSAL')
        AND status IN ('PENDING', 'APPROVED', 'DECLINED', 'ERROR', 'CANCELLED')
        AND char_length(currency) = 3
        AND amount > 0
        AND processed_at IS NOT NULL
    )
);
create index idx_payment_transactions_payment_id on payment_transactions (payment_id);
create index idx_payment_transactions_transaction_type on payment_transactions (transaction_type);
create index idx_payment_transactions_status on payment_transactions (status);
create index idx_payment_transactions_processed_at on payment_transactions (processed_at);
create index idx_payment_transactions_gateway_transaction_id on payment_transactions (gateway_transaction_id);

create table report_definitions (
    id uuid not null,
    restaurant_id uuid not null,
    branch_id uuid,
    code varchar(80) not null,
    name varchar(150) not null,
    report_type varchar(40) not null,
    frequency varchar(20) not null,
    format varchar(20) not null,
    schedule_expression varchar(100),
    timezone varchar(100),
    recipient_list text,
    filter_payload text,
    is_active boolean not null,
    last_run_at timestamptz,
    next_run_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    primary key (id),
    constraint uk_report_definitions_restaurant_code unique (restaurant_id, code),
    constraint fk_report_definitions_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_report_definitions_branch foreign key (branch_id) references branches(id),
    constraint fk_report_definitions_created_by_user foreign key (created_by) references users(id),
    constraint fk_report_definitions_updated_by_user foreign key (updated_by) references users(id),
    check (
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND report_type IN (
            'SALES_SUMMARY',
            'SHIFT_SUMMARY',
            'PAYMENT_SUMMARY',
            'INVENTORY_VALUATION',
            'INVENTORY_VARIANCE',
            'KITCHEN_PERFORMANCE',
            'MENU_PERFORMANCE',
            'LABOR_COST',
            'AUDIT_ACTIVITY',
            'EXCEPTION_REPORT'
        )
        AND frequency IN ('ON_DEMAND', 'HOURLY', 'DAILY', 'WEEKLY', 'MONTHLY')
        AND format IN ('PDF', 'CSV', 'XLSX', 'JSON')
        AND (
            frequency = 'ON_DEMAND'
            OR schedule_expression IS NOT NULL
        )
    )
);
create index idx_report_definitions_restaurant_id on report_definitions (restaurant_id);
create index idx_report_definitions_branch_id on report_definitions (branch_id);
create index idx_report_definitions_report_type on report_definitions (report_type);
create index idx_report_definitions_frequency on report_definitions (frequency);
create index idx_report_definitions_next_run_at on report_definitions (next_run_at);
create index idx_report_definitions_created_by on report_definitions (created_by);
create index idx_report_definitions_updated_by on report_definitions (updated_by);

create table report_executions (
    id uuid not null,
    report_definition_id uuid not null,
    requested_by uuid,
    status varchar(20) not null,
    period_start timestamptz,
    period_end timestamptz,
    started_at timestamptz,
    completed_at timestamptz,
    storage_uri text,
    row_count integer,
    file_checksum varchar(64),
    result_payload text,
    error_message text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint fk_report_executions_report_definition foreign key (report_definition_id) references report_definitions(id),
    constraint fk_report_executions_requested_by_user foreign key (requested_by) references users(id),
    check (
        status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')
        AND (row_count IS NULL OR row_count >= 0)
        AND (
            completed_at IS NULL
            OR started_at IS NOT NULL
        )
        AND (
            completed_at IS NULL
            OR completed_at >= started_at
        )
        AND (
            period_end IS NULL
            OR period_start IS NULL
            OR period_end >= period_start
        )
    )
);
create index idx_report_executions_report_definition_id on report_executions (report_definition_id);
create index idx_report_executions_status on report_executions (status);
create index idx_report_executions_requested_by on report_executions (requested_by);
create index idx_report_executions_started_at on report_executions (started_at);
create index idx_report_executions_completed_at on report_executions (completed_at);

create table notification_templates (
    id uuid not null,
    restaurant_id uuid not null,
    code varchar(80) not null,
    name varchar(150) not null,
    channel varchar(20) not null,
    subject_template varchar(150),
    body_template text,
    is_active boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    primary key (id),
    constraint uk_notification_templates_restaurant_code unique (restaurant_id, code),
    constraint fk_notification_templates_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_notification_templates_created_by_user foreign key (created_by) references users(id),
    constraint fk_notification_templates_updated_by_user foreign key (updated_by) references users(id),
    check (
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH', 'WEBHOOK')
    )
);
create index idx_notification_templates_restaurant_id on notification_templates (restaurant_id);
create index idx_notification_templates_channel on notification_templates (channel);
create index idx_notification_templates_created_by on notification_templates (created_by);
create index idx_notification_templates_updated_by on notification_templates (updated_by);

create table notification_preferences (
    id uuid not null,
    user_id uuid not null,
    channel varchar(20) not null,
    event_code varchar(80) not null,
    is_enabled boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint uk_notification_preferences_user_channel_event unique (user_id, channel, event_code),
    constraint fk_notification_preferences_user foreign key (user_id) references users(id),
    check (
        channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH', 'WEBHOOK')
        AND char_length(btrim(event_code)) > 0
    )
);
create index idx_notification_preferences_user_id on notification_preferences (user_id);
create index idx_notification_preferences_channel on notification_preferences (channel);

create table notifications (
    id uuid not null,
    restaurant_id uuid not null,
    branch_id uuid,
    template_id uuid,
    recipient_user_id uuid,
    channel varchar(20) not null,
    status varchar(20) not null,
    priority varchar(20) not null,
    event_code varchar(80) not null,
    subject varchar(150),
    body text,
    reference_type varchar(50),
    reference_id uuid,
    scheduled_at timestamptz,
    sent_at timestamptz,
    delivered_at timestamptz,
    read_at timestamptz,
    failure_reason varchar(255),
    attempt_count integer not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    primary key (id),
    constraint fk_notifications_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_notifications_branch foreign key (branch_id) references branches(id),
    constraint fk_notifications_template foreign key (template_id) references notification_templates(id),
    constraint fk_notifications_recipient_user foreign key (recipient_user_id) references users(id),
    constraint fk_notifications_created_by_user foreign key (created_by) references users(id),
    constraint fk_notifications_updated_by_user foreign key (updated_by) references users(id),
    check (
        channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH', 'WEBHOOK')
        AND status IN ('QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'READ', 'CANCELLED')
        AND priority IN ('LOW', 'NORMAL', 'HIGH', 'CRITICAL')
        AND char_length(btrim(event_code)) > 0
        AND attempt_count >= 0
        AND (
            delivered_at IS NULL
            OR sent_at IS NOT NULL
        )
        AND (
            read_at IS NULL
            OR delivered_at IS NOT NULL
        )
    )
);
create index idx_notifications_restaurant_id on notifications (restaurant_id);
create index idx_notifications_branch_id on notifications (branch_id);
create index idx_notifications_template_id on notifications (template_id);
create index idx_notifications_recipient_user_id on notifications (recipient_user_id);
create index idx_notifications_channel on notifications (channel);
create index idx_notifications_status on notifications (status);
create index idx_notifications_scheduled_at on notifications (scheduled_at);
create index idx_notifications_created_by on notifications (created_by);
create index idx_notifications_updated_by on notifications (updated_by);

create table audit_logs (
    id uuid not null,
    restaurant_id uuid not null,
    branch_id uuid,
    actor_user_id uuid,
    source varchar(20) not null,
    severity varchar(20) not null,
    entity_type varchar(50) not null,
    entity_id uuid,
    action varchar(80) not null,
    summary varchar(255) not null,
    reference_type varchar(50),
    reference_id uuid,
    before_state text,
    after_state text,
    metadata_payload text,
    ip_address varchar(45),
    user_agent varchar(255),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint fk_audit_logs_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_audit_logs_branch foreign key (branch_id) references branches(id),
    constraint fk_audit_logs_actor_user foreign key (actor_user_id) references users(id),
    check (
        source IN ('POS', 'BACK_OFFICE', 'API', 'WEBHOOK', 'SYSTEM', 'KDS', 'DEVICE')
        AND severity IN ('INFO', 'WARNING', 'CRITICAL')
        AND char_length(btrim(entity_type)) > 0
        AND char_length(btrim(action)) > 0
        AND char_length(btrim(summary)) > 0
    )
);
create index idx_audit_logs_restaurant_id on audit_logs (restaurant_id);
create index idx_audit_logs_branch_id on audit_logs (branch_id);
create index idx_audit_logs_actor_user_id on audit_logs (actor_user_id);
create index idx_audit_logs_entity_type on audit_logs (entity_type);
create index idx_audit_logs_action on audit_logs (action);
create index idx_audit_logs_source on audit_logs (source);
create index idx_audit_logs_created_at on audit_logs (created_at);
