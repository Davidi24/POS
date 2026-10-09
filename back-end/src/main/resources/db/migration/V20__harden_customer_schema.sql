alter table customers rename column code to customer_code;

alter table customers add column preferred_branch_id uuid;
alter table customers add column birth_date date;
alter table customers add column status varchar(30);
alter table customers add column last_visit_at timestamptz;
alter table customers add column total_visits integer not null default 0;
alter table customers add column lifetime_spend numeric(12, 2) not null default 0.00;

update customers
set status = case
    when is_active = true then 'ACTIVE'
    else 'INACTIVE'
end
where status is null;

alter table customers alter column status set not null;
alter table customers alter column status set default 'ACTIVE';
alter table customers alter column is_active set default true;

alter table customers drop constraint if exists customers_created_by_fkey;
alter table customers drop constraint if exists customers_updated_by_fkey;

alter table customers drop constraint if exists uk_customers_restaurant_code;
alter table customers add constraint uk_customers_restaurant_customer_code unique (restaurant_id, customer_code);

alter table customers add constraint fk_customers_created_by_user
    foreign key (created_by) references users(id);

alter table customers add constraint fk_customers_updated_by_user
    foreign key (updated_by) references users(id);

alter table customers add constraint fk_customers_preferred_branch
    foreign key (preferred_branch_id) references branches(id);

alter table customers drop constraint if exists customers_check;
alter table customers add constraint customers_check
    check (
        (customer_code IS NULL OR char_length(btrim(customer_code)) > 0)
        AND (
            first_name IS NOT NULL
            OR last_name IS NOT NULL
            OR email IS NOT NULL
            OR phone IS NOT NULL
        )
        AND (birth_date IS NULL OR birth_date <= CURRENT_DATE)
        AND status IN ('ACTIVE', 'INACTIVE', 'BLOCKED', 'ARCHIVED')
        AND total_visits >= 0
        AND lifetime_spend >= 0
    );

create index idx_customers_preferred_branch_id on customers (preferred_branch_id);
create index idx_customers_status on customers (status);

create table "customer-addresses" (
    id uuid not null,
    customer_id uuid not null,
    address_type varchar(30) not null,
    country varchar(100) not null,
    city varchar(100) not null,
    postal_code varchar(20),
    street_line_1 varchar(255) not null,
    street_line_2 varchar(255),
    is_default boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint fk_customer_addresses_customer foreign key (customer_id) references customers(id),
    check (
        char_length(btrim(country)) > 0
        AND char_length(btrim(city)) > 0
        AND char_length(btrim(street_line_1)) > 0
        AND address_type IN ('HOME', 'WORK', 'BILLING', 'SHIPPING', 'OTHER')
    )
);

create index idx_customer_addresses_customer_id on "customer-addresses" (customer_id);
create index idx_customer_addresses_type on "customer-addresses" (address_type);
create index idx_customer_addresses_is_default on "customer-addresses" (is_default);

create table "customer-notes" (
    id uuid not null,
    customer_id uuid not null,
    note_type varchar(30) not null,
    note text not null,
    is_private boolean not null default false,
    created_by uuid,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint fk_customer_notes_customer foreign key (customer_id) references customers(id),
    constraint fk_customer_notes_created_by_user foreign key (created_by) references users(id),
    check (
        char_length(btrim(note)) > 0
        AND note_type IN ('GENERAL', 'PREFERENCE', 'ALLERGY', 'SERVICE', 'INTERNAL')
    )
);

create index idx_customer_notes_customer_id on "customer-notes" (customer_id);
create index idx_customer_notes_note_type on "customer-notes" (note_type);
create index idx_customer_notes_created_by on "customer-notes" (created_by);

create table "customer-consents" (
    id uuid not null,
    customer_id uuid not null,
    consent_type varchar(30) not null,
    granted boolean not null,
    source varchar(30) not null,
    granted_at timestamptz,
    revoked_at timestamptz,
    note text,
    created_by uuid,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint fk_customer_consents_customer foreign key (customer_id) references customers(id),
    constraint fk_customer_consents_created_by_user foreign key (created_by) references users(id),
    check (
        consent_type IN ('MARKETING_EMAIL', 'MARKETING_SMS', 'PROFILING', 'THIRD_PARTY_SHARING', 'LOYALTY_PROGRAM')
        AND source IN ('WEB', 'MOBILE', 'POS', 'PHONE', 'IMPORT', 'ADMIN')
        AND (note IS NULL OR char_length(btrim(note)) > 0)
        AND (
            (granted = true AND granted_at IS NOT NULL AND revoked_at IS NULL)
            OR (granted = false AND revoked_at IS NOT NULL)
        )
        AND (granted_at IS NULL OR revoked_at IS NULL OR revoked_at >= granted_at)
    )
);

create index idx_customer_consents_customer_id on "customer-consents" (customer_id);
create index idx_customer_consents_consent_type on "customer-consents" (consent_type);
create index idx_customer_consents_created_by on "customer-consents" (created_by);
