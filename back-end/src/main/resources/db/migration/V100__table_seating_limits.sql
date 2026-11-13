-- How many guests a table takes (user, 2026-10-31): up to this many over its seats is fine; from seating_alert_from over,
-- people with these roles are told right away (role codes, comma separated). A table with 4 seats: 6 is fine, 8 tells them.
ALTER TABLE settings
    ADD COLUMN seating_extra_guests INTEGER NOT NULL DEFAULT 2,
    ADD COLUMN seating_alert_from INTEGER NOT NULL DEFAULT 4,
    ADD COLUMN seating_alert_roles VARCHAR(500) NOT NULL DEFAULT 'OWNER,MANAGER';
