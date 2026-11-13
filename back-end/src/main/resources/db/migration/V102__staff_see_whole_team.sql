-- POS → My shift → Team: staff see everyone working, or (off) only people in their own role.
ALTER TABLE settings ADD COLUMN staff_see_whole_team BOOLEAN NOT NULL DEFAULT TRUE;
