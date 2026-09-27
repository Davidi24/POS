-- Changes to a booking that aren't a status change, kept with who, when and why: the table held longer for a late
-- guest, how many of the group arrived, attendance confirmed, a no-show warning cleared, money refunded.
create table reservation_events (
    id uuid not null,
    reservation_id uuid not null,
    event_type varchar(40) not null,
    detail text,
    reason text,
    actor_id uuid references users(id),
    created_at timestamptz not null,
    primary key (id),
    constraint fk_reservation_events_reservation foreign key (reservation_id) references reservations(id) on delete cascade,
    check (char_length(btrim(event_type)) > 0)
);
create index idx_reservation_events_reservation on reservation_events (reservation_id, created_at);
