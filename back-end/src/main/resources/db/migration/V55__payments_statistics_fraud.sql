-- Taking payments at the POS, statistics and fraud checks (2026-10-06).

-- Cash handed over and the change given back, plus who voided a payment taken by mistake and why.
alter table payments add column tendered_amount numeric(19, 2);
alter table payments add column change_amount numeric(19, 2);
alter table payments add column voided_at timestamptz;
alter table payments add column voided_by uuid;
alter table payments add column void_reason text;
update payments set voided_at = updated_at where status = 'VOIDED' and voided_at is null;
alter table payments add constraint fk_payments_voided_by_user foreign key (voided_by) references users(id);
alter table payments add constraint ck_payments_cash_tender check (
    (tendered_amount is null or tendered_amount >= 0)
    and (change_amount is null or change_amount >= 0)
    and (status <> 'VOIDED' or voided_at is not null)
);
create index idx_payments_order_status on payments (order_id, status);
create index idx_payments_restaurant_paid_at on payments (restaurant_id, paid_at);

-- Who did each step (a refund, a void) and why.
alter table payment_transactions add column reason text;
alter table payment_transactions add column created_by uuid;
alter table payment_transactions add constraint fk_payment_transactions_created_by_user foreign key (created_by) references users(id);
create index idx_payment_transactions_created_by on payment_transactions (created_by);

-- Who removed an item and when it went to the kitchen: fraud checks look for items removed after cooking started.
alter table order_line_items add column fired_at timestamptz;
alter table order_line_items add column voided_at timestamptz;
alter table order_line_items add column voided_by uuid;
alter table order_line_items add constraint fk_order_line_items_voided_by_user foreign key (voided_by) references users(id);
create index idx_order_line_items_voided_at on order_line_items (voided_at) where voided_at is not null;

-- Items already cooked or served before this change count as sent to the kitchen.
update order_line_items set fired_at = updated_at where status in ('FIRED', 'READY', 'FULFILLED') and fired_at is null;

create index idx_orders_restaurant_closed_at on orders (restaurant_id, closed_at);
create index idx_orders_restaurant_opened_at on orders (restaurant_id, opened_at);
create index idx_order_events_type_created on order_events (event_type, created_at);

-- Admin Hub → Settings → Payments & receipts: tips and what happens once a bill is paid.
alter table settings add column tips_enabled boolean not null default true;
alter table settings add column tip_suggestions varchar(50) not null default '5,10,15';
alter table settings add column max_tip_percent integer not null default 50;
alter table settings add column auto_close_paid_orders boolean not null default true;
alter table settings add column refund_window_days integer not null default 30;
alter table settings add constraint ck_settings_payments check (
    max_tip_percent between 1 and 200
    and refund_window_days between 0 and 365
    and tip_suggestions ~ '^[0-9]{1,3}(,[0-9]{1,3}){0,5}$'
);

-- Admin Hub → Settings → Fraud checks: when an action is flagged for an owner to look at.
alter table settings add column fraud_discount_percent integer not null default 30;
alter table settings add column fraud_refund_amount numeric(12, 2) not null default 50.00;
alter table settings add column fraud_voids_per_day integer not null default 5;
alter table settings add column fraud_tip_percent integer not null default 30;
alter table settings add column fraud_cash_refunds_per_day integer not null default 2;
alter table settings add column fraud_disabled_rules varchar(500) not null default '';
alter table settings add constraint ck_settings_fraud check (
    fraud_discount_percent between 1 and 100
    and fraud_refund_amount >= 0 and fraud_refund_amount <= 1000000
    and fraud_voids_per_day between 1 and 1000
    and fraud_tip_percent between 1 and 500
    and fraud_cash_refunds_per_day between 1 and 1000
);

-- An owner's verdict on a flagged action. Alerts themselves are worked out from orders and payments each time.
create table fraud_alert_reviews (
    id uuid not null,
    restaurant_id uuid not null,
    alert_key varchar(200) not null,
    status varchar(20) not null,
    note text,
    reviewed_by uuid,
    reviewed_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint uk_fraud_alert_reviews_key unique (restaurant_id, alert_key),
    constraint fk_fraud_alert_reviews_restaurant foreign key (restaurant_id) references restaurants(id) on delete cascade,
    constraint fk_fraud_alert_reviews_user foreign key (reviewed_by) references users(id),
    constraint ck_fraud_alert_reviews_status check (status in ('OPEN', 'REVIEWED', 'DISMISSED', 'CONFIRMED'))
);
create index idx_fraud_alert_reviews_restaurant on fraud_alert_reviews (restaurant_id);

-- Custom roles belong to the restaurant that made them; system roles (and older custom roles) stay shared.
alter table roles add column restaurant_id uuid;
alter table roles add constraint fk_roles_restaurant foreign key (restaurant_id) references restaurants(id) on delete cascade;
create index idx_roles_restaurant_id on roles (restaurant_id);
