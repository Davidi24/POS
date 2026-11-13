-- No usual shifts to start with (user, 2026-10-31: "no default shifts, I choose them from the beginning"). Takes out what
-- V93 seeded: only rows still exactly as seeded (never changed, for everyone). Shifts a restaurant made or changed stay,
-- and shifts already on the calendar aren't linked to these, so they stay too.
DELETE FROM shift_templates t
WHERE t.updated_at = t.created_at
  AND NOT EXISTS (SELECT 1 FROM shift_template_roles r WHERE r.template_id = t.id)
  AND (t.name, t.start_time, t.end_time, t.sort_order) IN (
      ('Morning', TIME '09:00', TIME '17:00', 1),
      ('Lunch', TIME '10:00', TIME '16:00', 2),
      ('Afternoon', TIME '15:00', TIME '23:00', 3),
      ('Evening', TIME '16:00', TIME '23:30', 4),
      ('Closing', TIME '17:00', TIME '00:30', 5));
