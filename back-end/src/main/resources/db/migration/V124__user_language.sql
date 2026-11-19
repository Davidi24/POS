-- The language each person picked for the app (user, 2026-11-18: "each person makes a default in profile"): en, it, es or sq.
-- Empty = English until they choose.
ALTER TABLE users ADD COLUMN language VARCHAR(5);
ALTER TABLE users ADD CONSTRAINT users_language_check CHECK (language IS NULL OR language IN ('en', 'it', 'es', 'sq'));
