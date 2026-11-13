alter table settings
    add column inventory_count_frequency integer not null default 1,
    add column inventory_count_period varchar(10) not null default 'WEEKLY',
    add column inventory_count_schedule_started_at timestamptz not null default now();

alter table settings
    add constraint ck_settings_inventory_count_schedule check (
        inventory_count_frequency between 1 and 24
        and inventory_count_period in ('DAILY', 'WEEKLY')
        and (inventory_count_period <> 'WEEKLY' or inventory_count_frequency <= 7)
    );
