-- The language a guest booked in (user, 2026-11-18: "English and the one they booked in, so English is always included"):
-- their booking emails come in it, with an English version under it. Empty = English only (bookings staff made).
ALTER TABLE reservations ADD COLUMN language VARCHAR(5);
ALTER TABLE reservations ADD CONSTRAINT reservations_language_check CHECK (language IS NULL OR language IN ('en', 'it', 'es', 'sq'));
