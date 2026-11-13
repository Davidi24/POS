-- Reservation rules, phase 2 (agreed with the owner, 2026-09-27).

-- "✓ Attendance confirmed": the guest said they're still coming (staff after a call, or the guest from the reminder).
-- A mark on a confirmed booking, not a status.
alter table reservations add column attendance_confirmed_at timestamptz;
alter table reservations add column attendance_confirmed_by uuid references users(id);
alter table reservations add column attendance_confirmed_via varchar(20);
alter table reservations add constraint ck_reservations_attendance_via
    check (attendance_confirmed_via is null or attendance_confirmed_via in ('STAFF', 'GUEST'));

-- A manager cleared a guest's no-show warning. The no-show bookings stay; only no-shows after the latest clear
-- count for the warning. The guest is matched by customer, or by phone/email for bookings without one.
create table guest_no_show_clears (
    id uuid not null,
    restaurant_id uuid not null references restaurants(id),
    customer_id uuid references customers(id),
    contact_phone varchar(50),
    contact_email varchar(150),
    reason text not null,
    cleared_by uuid references users(id),
    cleared_at timestamptz not null,
    primary key (id),
    check (customer_id is not null or contact_phone is not null or contact_email is not null),
    check (char_length(btrim(reason)) > 0)
);
create index idx_guest_no_show_clears_restaurant on guest_no_show_clears (restaurant_id);

-- Walk-ins waiting at the door for a table.
create table waitlist_entries (
    id uuid not null,
    restaurant_id uuid not null references restaurants(id),
    branch_id uuid not null references branches(id),
    guest_name varchar(150) not null,
    contact_phone varchar(50),
    party_size integer not null,
    note text,
    status varchar(20) not null,
    table_id uuid references tables(id),
    created_at timestamptz not null,
    created_by uuid references users(id),
    seated_at timestamptz,
    left_at timestamptz,
    updated_at timestamptz not null,
    updated_by uuid references users(id),
    primary key (id),
    check (char_length(btrim(guest_name)) > 0),
    check (party_size > 0),
    check (status in ('WAITING', 'SEATED', 'LEFT')),
    check (seated_at is null or status = 'SEATED'),
    check (left_at is null or status = 'LEFT')
);
create index idx_waitlist_entries_branch_status on waitlist_entries (branch_id, status, created_at);

-- Reminders sent once a day per restaurant (e.g. "3 bookings tomorrow aren't confirmed yet" at 15:00).
create table reservation_reminders (
    restaurant_id uuid not null references restaurants(id),
    kind varchar(40) not null,
    service_date date not null,
    sent_at timestamptz not null,
    primary key (restaurant_id, kind, service_date)
);
