-- Reservation rules, phase 1 (agreed with the owner, 2026-09-27).
-- hold_until: how long the table waits for a late guest; empty means the booking time plus the hold setting.
-- arrived_guests: "3 of 6 arrived" while the rest of the group is still coming.
-- expired_at: a request nobody answered before the booking time. It's never a no-show.
alter table reservations add column hold_until timestamptz;
alter table reservations add column arrived_guests integer;
alter table reservations add column expired_at timestamptz;

alter table reservations drop constraint reservations_check;
alter table reservations add constraint reservations_check check (
    char_length(btrim(reservation_code)) > 0
    AND source IN ('INTERNAL', 'WEB', 'MOBILE', 'PHONE', 'WALK_IN', 'THIRD_PARTY')
    AND status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'SEATED', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'EXPIRED')
    AND deposit_status IN ('NOT_REQUIRED', 'PENDING', 'PAID', 'PARTIALLY_PAID', 'REFUNDED', 'FORFEITED', 'WAIVED')
    AND party_size > 0
    AND reservation_end > reservation_start
    AND (
        customer_id IS NOT NULL
        OR contact_name IS NOT NULL
        OR contact_phone IS NOT NULL
        OR contact_email IS NOT NULL
    )
    AND (deposit_amount IS NULL OR deposit_amount >= 0)
    AND (
        deposit_required = true
        OR (deposit_amount IS NULL AND deposit_status = 'NOT_REQUIRED')
    )
    AND (
        deposit_required = false
        OR (deposit_amount IS NOT NULL AND deposit_amount > 0 AND deposit_status <> 'NOT_REQUIRED')
    )
    AND (cancelled_at IS NULL OR status = 'CANCELLED')
    AND (no_show_at IS NULL OR status = 'NO_SHOW')
    AND (expired_at IS NULL OR status = 'EXPIRED')
    AND (completed_at IS NULL OR status = 'COMPLETED')
    AND (
        checked_in_at IS NULL
        OR status IN ('CHECKED_IN', 'SEATED', 'COMPLETED')
    )
    AND (
        seated_at IS NULL
        OR status IN ('SEATED', 'COMPLETED')
    )
    AND (
        confirmed_at IS NULL
        OR status <> 'PENDING'
    )
    AND (arrived_guests IS NULL OR arrived_guests > 0)
    AND (hold_until IS NULL OR hold_until > reservation_start)
);

alter table "reservation-status-history" drop constraint "reservation-status-history_check";
alter table "reservation-status-history" add constraint "reservation-status-history_check" check (
    (old_status IS NULL OR old_status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'SEATED', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'EXPIRED'))
    AND new_status IN ('PENDING', 'CONFIRMED', 'CHECKED_IN', 'SEATED', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'EXPIRED')
    AND (old_status IS NULL OR old_status <> new_status)
);

-- The no-show job looks for confirmed bookings past their hold and requests past their start.
create index idx_reservations_status_start on reservations (status, reservation_start);
