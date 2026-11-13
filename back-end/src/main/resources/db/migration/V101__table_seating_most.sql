-- A table now has a hard most (user, 2026-10-31): seats + this many guests, 4 seats: 8. Between the normal number and that it's
-- allowed but unusual, and people get told.
ALTER TABLE settings RENAME COLUMN seating_alert_from TO seating_most_extra_guests;
