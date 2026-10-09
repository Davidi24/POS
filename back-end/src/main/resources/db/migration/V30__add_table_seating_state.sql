alter table tables
    add column guest_count integer,
    add column seated_at timestamptz;

update tables
set guest_count = capacity,
    seated_at = coalesce(updated_at, now())
where status = 'OCCUPIED';

alter table tables
    add constraint ck_tables_seating_state check (
        (
            status = 'OCCUPIED'
            and guest_count is not null
            and guest_count > 0
            and seated_at is not null
        )
        or (
            status <> 'OCCUPIED'
            and guest_count is null
            and seated_at is null
        )
    );
