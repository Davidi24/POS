create table reservation_notes (
    id uuid not null,
    reservation_id uuid not null,
    note text not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users(id),
    updated_by uuid references users(id),
    primary key (id),
    constraint fk_reservation_notes_reservation foreign key (reservation_id) references reservations(id),
    check (char_length(btrim(note)) > 0)
);

create index idx_reservation_notes_reservation_id on reservation_notes (reservation_id);
create index idx_reservation_notes_created_by on reservation_notes (created_by);
create index idx_reservation_notes_updated_by on reservation_notes (updated_by);
