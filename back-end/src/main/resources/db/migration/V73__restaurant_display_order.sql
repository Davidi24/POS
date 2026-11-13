-- The order of the restaurant cards on the super admins' restaurant list (changed with "Change position").
ALTER TABLE restaurants ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0;
UPDATE restaurants r SET display_order = s.position
FROM (SELECT id, (row_number() OVER (ORDER BY lower(name), id) - 1)::int AS position FROM restaurants) s
WHERE s.id = r.id;
