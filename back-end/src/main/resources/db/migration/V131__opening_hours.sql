-- Opening hours (user, 2026-11-20, step 4): each branch its own week, any number of openings a day (each from when it
-- opens to when it closes; closing at or before opening runs past midnight), special days for a date or a range,
-- closed or with their own openings, and "Open outside the hours" for now. A branch whose hours were never set is
-- treated as always open.
ALTER TABLE branches ADD COLUMN opening_hours_set boolean NOT NULL DEFAULT false;

CREATE TABLE branch_opening_hours (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES branches (id) ON DELETE CASCADE,
    day_of_week smallint NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    opens time NOT NULL,
    closes time NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (opens <> closes)
);
CREATE INDEX idx_branch_opening_hours_branch ON branch_opening_hours (branch_id, day_of_week);

CREATE TABLE branch_special_days (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES branches (id) ON DELETE CASCADE,
    starts_on date NOT NULL,
    ends_on date NOT NULL,
    closed boolean NOT NULL,
    note varchar(255),
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid REFERENCES users (id),
    CHECK (ends_on >= starts_on)
);
CREATE INDEX idx_branch_special_days_branch ON branch_special_days (branch_id, starts_on, ends_on);

CREATE TABLE branch_special_day_hours (
    id uuid PRIMARY KEY,
    special_day_id uuid NOT NULL REFERENCES branch_special_days (id) ON DELETE CASCADE,
    opens time NOT NULL,
    closes time NOT NULL,
    CHECK (opens <> closes)
);
CREATE INDEX idx_branch_special_day_hours_day ON branch_special_day_hours (special_day_id);

CREATE TABLE branch_open_overrides (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES branches (id) ON DELETE CASCADE,
    opened_at timestamptz NOT NULL,
    open_until timestamptz NOT NULL,
    opened_by uuid REFERENCES users (id),
    closed_at timestamptz,
    CHECK (open_until > opened_at)
);
CREATE INDEX idx_branch_open_overrides_branch ON branch_open_overrides (branch_id, open_until);

-- What was set the old way (one opening a day, one special date) carries over.
INSERT INTO branch_opening_hours (id, branch_id, day_of_week, opens, closes, created_at)
SELECT gen_random_uuid(), h.branch_id, h.day_of_week, h.open_time, h.close_time, now()
FROM "settings-business-hours" h
WHERE NOT h.is_closed AND h.open_time IS NOT NULL AND h.close_time IS NOT NULL AND h.open_time <> h.close_time;
UPDATE branches SET opening_hours_set = true WHERE id IN (SELECT branch_id FROM "settings-business-hours");

INSERT INTO branch_special_days (id, branch_id, starts_on, ends_on, closed, note, created_at)
SELECT s.id, s.branch_id, s.special_date, s.special_date, s.is_closed, s.note, s.created_at FROM "settings-special-hours" s;
INSERT INTO branch_special_day_hours (id, special_day_id, opens, closes)
SELECT gen_random_uuid(), s.id, s.open_time, s.close_time FROM "settings-special-hours" s
WHERE NOT s.is_closed AND s.open_time IS NOT NULL AND s.close_time IS NOT NULL;

-- The two new permissions (startup seeding names them and gives them to the built-in roles); a restaurant's own roles
-- that change settings may change the hours too.
INSERT INTO permissions (id, code, name, description, created_at)
SELECT gen_random_uuid(), v.code, v.code, NULL, now() FROM (VALUES ('HOURS_UPDATE'), ('OPEN_OUTSIDE_HOURS')) AS v(code)
ON CONFLICT (code) DO NOTHING;
INSERT INTO role_permissions (id, role_id, permission_id, created_at)
SELECT gen_random_uuid(), rp.role_id, np.id, now()
FROM role_permissions rp
JOIN roles r ON r.id = rp.role_id AND NOT r.is_system
JOIN permissions op ON op.id = rp.permission_id AND op.code = 'SETTINGS_UPDATE'
JOIN permissions np ON np.code = 'HOURS_UPDATE'
ON CONFLICT (role_id, permission_id) DO NOTHING;
