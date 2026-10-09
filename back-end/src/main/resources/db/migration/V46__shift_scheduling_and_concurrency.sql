-- Planned shifts do not have an actual clock-in time.
ALTER TABLE shifts ALTER COLUMN started_at DROP NOT NULL;
ALTER TABLE shifts ADD COLUMN version bigint NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX uq_shift_active_user ON shifts(user_id) WHERE status IN ('OPEN', 'ON_BREAK');
CREATE UNIQUE INDEX uq_shift_open_break ON shift_breaks(shift_id) WHERE ended_at IS NULL;
CREATE INDEX idx_shift_schedule_branch ON shifts(branch_id, scheduled_start);
CREATE INDEX idx_shift_schedule_user ON shifts(user_id, scheduled_start, scheduled_end);
