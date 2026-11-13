-- "More will come" is a mark, not a number (user, 2026-11-01: "don't ask how many, I do that only when I don't know"):
-- the half ring on the table until staff untick it.
ALTER TABLE tables ADD COLUMN more_coming BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE tables SET more_coming = TRUE WHERE expected_guests IS NOT NULL;
ALTER TABLE tables DROP COLUMN expected_guests;
