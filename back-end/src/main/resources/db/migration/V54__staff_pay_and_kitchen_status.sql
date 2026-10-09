-- Staff pay, the waiter's Kitchen Status and clocking in (agreed with the owner, 2026-09-28).

-- Each staff member's hourly wage, set by a manager in Admin Hub → Shifts → Hours & pay. A shift keeps the wage it
-- was worked at (shifts.hourly_rate, filled at clock-in), so a raise never changes past pay.
create table staff_pay_rates (
    user_id uuid not null,
    restaurant_id uuid not null,
    hourly_rate numeric(12, 2) not null,
    updated_at timestamptz not null,
    updated_by uuid,
    primary key (user_id),
    constraint fk_staff_pay_rates_user foreign key (user_id) references users(id) on delete cascade,
    constraint fk_staff_pay_rates_restaurant foreign key (restaurant_id) references restaurants(id) on delete cascade,
    constraint ck_staff_pay_rates_rate check (hourly_rate >= 0 and hourly_rate <= 10000)
);
create index idx_staff_pay_rates_restaurant on staff_pay_rates (restaurant_id);

-- Admin Hub → Settings → Orders & kitchen: when the waiters' Kitchen Status marks an order as taking long, and ready
-- food as waiting too long to be picked up.
alter table settings add column kitchen_slow_after_minutes integer not null default 20;
alter table settings add column kitchen_ready_waiting_minutes integer not null default 5;
alter table settings add constraint ck_settings_kitchen_status
    check (kitchen_slow_after_minutes between 1 and 240 and kitchen_ready_waiting_minutes between 1 and 120);

-- Admin Hub → Settings → Shifts: how early staff may clock in for a scheduled shift (it was a fixed 2 hours).
alter table settings add column clock_in_early_minutes integer not null default 120;
alter table settings add constraint ck_settings_clock_in_early check (clock_in_early_minutes between 0 and 720);
