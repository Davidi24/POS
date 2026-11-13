-- Special cases (user, 2026-11-01): what the system does when nobody acts, all of it changeable in Settings → Tables and
-- undoable. Limits are minutes without any action on the table or its order; 0 switches one off.
ALTER TABLE settings
    ADD COLUMN special_case_paid_minutes INTEGER NOT NULL DEFAULT 60,
    ADD COLUMN special_case_seated_minutes INTEGER NOT NULL DEFAULT 240,
    ADD COLUMN special_case_order_minutes INTEGER NOT NULL DEFAULT 240,
    ADD COLUMN special_case_warning_minutes INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN special_case_auto_move BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN special_case_booking_no_action BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN special_case_more_time VARCHAR(30) NOT NULL DEFAULT '10,15,30',
    ADD COLUMN special_case_alert_roles VARCHAR(500) NOT NULL DEFAULT 'OWNER,MANAGER';

ALTER TABLE tables
    ADD COLUMN more_time_until TIMESTAMPTZ,
    ADD COLUMN still_here_at TIMESTAMPTZ;
-- The clocks start now, so nothing old is closed the moment this goes live.
UPDATE tables SET still_here_at = now();

ALTER TABLE reservations ADD COLUMN no_action_at TIMESTAMPTZ;

DO $$
DECLARE definition TEXT;
BEGIN
    SELECT pg_get_constraintdef(oid) INTO definition FROM pg_constraint
        WHERE conname = 'reservations_check' AND conrelid = 'reservations'::regclass;
    EXECUTE 'ALTER TABLE reservations DROP CONSTRAINT reservations_check';
    EXECUTE 'ALTER TABLE reservations ADD CONSTRAINT reservations_check '
        || replace(definition, '''EXPIRED''::character varying', '''EXPIRED''::character varying, ''NO_ACTION''::character varying');

    SELECT pg_get_constraintdef(oid) INTO definition FROM pg_constraint
        WHERE conname = 'reservation-status-history_check' AND conrelid = '"reservation-status-history"'::regclass;
    EXECUTE 'ALTER TABLE "reservation-status-history" DROP CONSTRAINT "reservation-status-history_check"';
    EXECUTE 'ALTER TABLE "reservation-status-history" ADD CONSTRAINT "reservation-status-history_check" '
        || replace(definition, '''EXPIRED''::character varying', '''EXPIRED''::character varying, ''NO_ACTION''::character varying');
END $$;

-- What the system did by itself, so someone can see it and return it.
CREATE TABLE special_case_actions (
    id UUID PRIMARY KEY,
    restaurant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    kind VARCHAR(30) NOT NULL,
    table_id UUID,
    table_number VARCHAR(30),
    moved_to VARCHAR(30),
    order_ids VARCHAR(400),
    reservation_id UUID,
    guests_by_table VARCHAR(2000),
    seated_at TIMESTAMPTZ,
    summary VARCHAR(300) NOT NULL,
    happened_at TIMESTAMPTZ NOT NULL,
    returned_at TIMESTAMPTZ,
    returned_by UUID
);
CREATE INDEX idx_special_case_actions_branch ON special_case_actions (branch_id, happened_at DESC);
CREATE INDEX idx_special_case_actions_reservation ON special_case_actions (reservation_id);
