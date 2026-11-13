-- The icon picked for a usual shift (user, 2026-10-31: "let me choose the icon here too"), one of the app's by name. Without
-- one the app shows the part of the day the shift starts in.
ALTER TABLE shift_templates ADD COLUMN icon VARCHAR(40);
