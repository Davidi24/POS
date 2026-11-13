-- Guests still at a table this long before its next booking: the table warns (user, 2026-11-01).
ALTER TABLE settings ADD COLUMN next_booking_warning_minutes INTEGER NOT NULL DEFAULT 15;
