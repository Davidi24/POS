-- Reservation rules, phase 4 (agreed with the owner, 2026-09-27): website bookings, guest emails with links, and
-- online payments with refunds.

-- The secret in the guest's links (confirm, cancel, running late, pay). Every booking has one.
alter table reservations add column guest_token varchar(64);
update reservations set guest_token = replace(gen_random_uuid()::text, '-', '') where guest_token is null;
alter table reservations alter column guest_token set not null;
create unique index uk_reservations_guest_token on reservations (guest_token);

-- The payment provider's fee, kept back on refunds (e.g. 1.5 % + €0.25): Admin Hub → Settings → Reservations.
alter table settings add column card_fee_percent numeric(5, 2) not null default 1.50;
alter table settings add column card_fee_fixed numeric(10, 2) not null default 0.25;
alter table settings add constraint ck_settings_card_fee
    check (card_fee_percent between 0 and 20 and card_fee_fixed between 0 and 10);

-- Money taken for a booking: a deposit (big groups, optional) or paid extras (a cake, decoration…). Pre-orders keep
-- their own record in pre_orders.
create table reservation_payments (
    id uuid not null,
    reservation_id uuid not null,
    kind varchar(20) not null,
    description varchar(200) not null,
    amount numeric(19, 2) not null,
    currency varchar(3) not null,
    status varchar(20) not null,
    -- Cancelled before this: refunded minus the card fee. After it (or a no-show): kept.
    refund_deadline timestamptz,
    refunded_amount numeric(19, 2) not null default 0,
    card_fee numeric(19, 2),
    provider varchar(30),
    provider_ref varchar(120),
    menu_item_id uuid,
    quantity integer,
    paid_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint fk_reservation_payments_reservation foreign key (reservation_id) references reservations(id) on delete cascade,
    check (kind in ('DEPOSIT', 'EXTRA')),
    check (status in ('PENDING', 'PAID', 'REFUNDED', 'KEPT', 'CANCELLED')),
    check (amount > 0),
    check (refunded_amount >= 0 and refunded_amount <= amount),
    check (quantity is null or quantity > 0)
);
create index idx_reservation_payments_reservation on reservation_payments (reservation_id);

-- Pre-orders refunded after a cancel: the money back (the paid amount minus the card fee, or all of it when the
-- restaurant declined), and goodwill refunds on kept ones.
alter table pre_orders add column refunded_amount numeric(19, 2) not null default 0;
