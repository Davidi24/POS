-- Reservation rules, phase 3 (agreed with the owner, 2026-09-27): occasions, special menus and event nights.

-- Occasions a booking can have (Birthday, Anniversary…), each with its own icon and options. Editable in the Admin
-- Hub; the defaults are created the first time a restaurant opens them.
create table reservation_occasions (
    id uuid not null,
    restaurant_id uuid not null references restaurants(id),
    code varchar(40) not null,
    name varchar(80) not null,
    icon varchar(16) not null,
    -- One option per line, e.g. "Cake from us" / "Candles" / "Birthday song".
    options text not null default '',
    active boolean not null default true,
    display_order integer not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint uk_reservation_occasions_code unique (restaurant_id, code),
    check (char_length(btrim(code)) > 0),
    check (char_length(btrim(name)) > 0),
    check (char_length(btrim(icon)) > 0)
);

-- The booking keeps what was picked, even if the occasion is renamed or removed later.
alter table reservations add column occasion_code varchar(40);
alter table reservations add column occasion_name varchar(80);
alter table reservations add column occasion_icon varchar(16);
alter table reservations add column occasion_options text;
alter table reservations add column occasion_note text;

-- The restaurant's own nights (New Year's Eve, Valentine's…): a special menu that day, normal bookings.
create table restaurant_events (
    id uuid not null,
    restaurant_id uuid not null references restaurants(id),
    name varchar(120) not null,
    icon varchar(16) not null,
    start_date date not null,
    end_date date not null,
    menu_id uuid references menus(id) on delete set null,
    -- That evening the POS offers only the special menu (otherwise special and normal menus).
    special_menu_only boolean not null default false,
    active boolean not null default true,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users(id),
    updated_by uuid references users(id),
    primary key (id),
    check (char_length(btrim(name)) > 0),
    check (end_date >= start_date)
);
create index idx_restaurant_events_dates on restaurant_events (restaurant_id, start_date, end_date);

-- Special menus (occasion extras, event menus). Their items can be copies imported from other menus and may have
-- to be ordered some hours ahead (e.g. a cake 48 h before), and may be offered only for some occasions.
alter table menus add column is_special boolean not null default false;
alter table "menu-items" add column order_before_hours integer;
alter table "menu-items" add constraint ck_menu_items_order_before_hours
    check (order_before_hours is null or order_before_hours between 0 and 720);
alter table "menu-items" add column occasion_codes varchar(500);
