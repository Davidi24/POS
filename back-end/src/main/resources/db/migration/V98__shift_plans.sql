-- How a usual shift runs at a branch (user, 2026-10-31: "who it applies to, and one week the first shift, one week the second"):
-- the weekdays ("0,1,2,3,4", 0 = Monday), from which day, for how long (length_unit DAYS, WEEKS or MONTHS with length_count; no
-- unit = forever), the people it applies to, and the shifts it takes turns with (turn_shifts, template ids in order after this
-- one, each turn lasting turn_every weeks; a person's start_turn says which turn they start on). ShiftPlanService makes the
-- real shifts some weeks ahead and marks them with plan_id; planned_until is the last day made.
CREATE TABLE shift_plans (
    id            UUID PRIMARY KEY,
    template_id   UUID        NOT NULL REFERENCES shift_templates (id) ON DELETE CASCADE,
    branch_id     UUID        NOT NULL REFERENCES branches (id) ON DELETE CASCADE,
    weekdays      VARCHAR(13) NOT NULL,
    starts_on     DATE        NOT NULL,
    length_unit   VARCHAR(10),
    length_count  INT,
    turn_every    INT         NOT NULL DEFAULT 1,
    turn_shifts   TEXT,
    planned_until DATE,
    updated_by    UUID REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_shift_plans UNIQUE (template_id, branch_id)
);

CREATE TABLE shift_plan_people (
    plan_id    UUID NOT NULL REFERENCES shift_plans (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    start_turn INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (plan_id, user_id)
);

ALTER TABLE shifts ADD COLUMN plan_id UUID REFERENCES shift_plans (id) ON DELETE SET NULL;
CREATE INDEX idx_shifts_plan ON shifts (plan_id) WHERE plan_id IS NOT NULL;

-- What V97 kept on the shift goes to the restaurant's main branch.
INSERT INTO shift_plans (id, template_id, branch_id, weekdays, starts_on, length_unit, length_count)
SELECT gen_random_uuid(), t.id, b.id, t.weekdays, t.starts_on, t.length_unit, t.length_count
FROM shift_templates t
CROSS JOIN LATERAL (SELECT id FROM branches b WHERE b.restaurant_id = t.restaurant_id AND b.deleted_at IS NULL
                    ORDER BY (b.code = 'MAIN') DESC, b.created_at LIMIT 1) b
WHERE t.weekdays IS NOT NULL AND t.starts_on IS NOT NULL;

ALTER TABLE shift_templates DROP COLUMN weekdays, DROP COLUMN starts_on, DROP COLUMN length_unit, DROP COLUMN length_count;
