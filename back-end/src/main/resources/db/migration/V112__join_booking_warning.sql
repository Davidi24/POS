-- Joining a table whose booking comes within this many minutes asks what to do with the booking (user, 2026-11-01).
ALTER TABLE settings ADD COLUMN join_booking_warning_minutes INTEGER NOT NULL DEFAULT 120;
