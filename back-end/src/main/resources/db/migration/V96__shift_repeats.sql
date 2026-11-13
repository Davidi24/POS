-- Shifts that go on until someone stops them (user, 2026-10-31: "a duration: forever, or days, weeks or months"). A repeat
-- is one person at one time on some weekdays ("0,1,2,3,4", 0 = Monday). Its shifts are normal shifts, made some weeks ahead
-- and topped up by ShiftRepeatService; planned_until is the last day already made.
CREATE TABLE shift_repeats (
    id            UUID PRIMARY KEY,
    restaurant_id UUID        NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    branch_id     UUID        NOT NULL REFERENCES branches (id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    start_time    TIME        NOT NULL,
    end_time      TIME        NOT NULL,
    weekdays      VARCHAR(13) NOT NULL,
    notes         VARCHAR(2000),
    planned_until DATE        NOT NULL,
    stopped_at    TIMESTAMPTZ,
    created_by    UUID REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_shift_repeats_times CHECK (start_time <> end_time)
);

CREATE INDEX idx_shift_repeats_open ON shift_repeats (user_id) WHERE stopped_at IS NULL;

ALTER TABLE shifts ADD COLUMN repeat_id UUID REFERENCES shift_repeats (id) ON DELETE SET NULL;
CREATE INDEX idx_shifts_repeat ON shifts (repeat_id) WHERE repeat_id IS NOT NULL;
