-- Stable branch calendar/list pages ordered by start time then id.
create index idx_reservations_branch_start_page
    on reservations (branch_id, reservation_start, id);
