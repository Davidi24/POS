-- The second line of a special case's row, and who to reopen: guests or the booking's size.
ALTER TABLE special_case_actions
    ADD COLUMN detail VARCHAR(300),
    ADD COLUMN guests INTEGER;
