-- Seated with more guests still to come (user, 2026-11-01): how many there will be in all; null when everyone is here.
ALTER TABLE tables ADD COLUMN expected_guests INTEGER;
