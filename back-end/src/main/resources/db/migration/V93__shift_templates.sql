-- The restaurant's usual shifts, kept on the server (user, 2026-10-31: "I create different shifts and edit them, each for
-- the people it's for"): a name, start and end (an end at or before the start is the next day), and the roles it's for
-- (none = everyone). They were kept in the app before; every restaurant starts with the app's five.
CREATE TABLE shift_templates (
    id            UUID PRIMARY KEY,
    restaurant_id UUID        NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    name          VARCHAR(60) NOT NULL,
    start_time    TIME        NOT NULL,
    end_time      TIME        NOT NULL,
    sort_order    INT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_shift_templates_times CHECK (start_time <> end_time)
);

CREATE INDEX idx_shift_templates_restaurant ON shift_templates (restaurant_id, sort_order);

CREATE TABLE shift_template_roles (
    template_id UUID NOT NULL REFERENCES shift_templates (id) ON DELETE CASCADE,
    role_id     UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (template_id, role_id)
);

INSERT INTO shift_templates (id, restaurant_id, name, start_time, end_time, sort_order)
SELECT gen_random_uuid(), r.id, t.name, t.start_time, t.end_time, t.sort_order
FROM restaurants r
CROSS JOIN (VALUES ('Morning', TIME '09:00', TIME '17:00', 1),
                   ('Lunch', TIME '10:00', TIME '16:00', 2),
                   ('Afternoon', TIME '15:00', TIME '23:00', 3),
                   ('Evening', TIME '16:00', TIME '23:30', 4),
                   ('Closing', TIME '17:00', TIME '00:30', 5)) AS t (name, start_time, end_time, sort_order);
