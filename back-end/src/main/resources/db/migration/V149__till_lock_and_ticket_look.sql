-- Decided 2026-11-28: the till lock and the PIN pause are settings (Admin Hub → Settings → Devices & printing), and each
-- kitchen station picks its tickets' letter size and whether notes print in red (on two-colour printers).
ALTER TABLE settings ADD COLUMN till_lock_seconds integer NOT NULL DEFAULT 30 CHECK (till_lock_seconds BETWEEN 10 AND 1800);
ALTER TABLE settings ADD COLUMN till_lock_important_seconds integer NOT NULL DEFAULT 180 CHECK (till_lock_important_seconds BETWEEN 30 AND 3600);
ALTER TABLE settings ADD COLUMN pin_pause_after integer NOT NULL DEFAULT 10 CHECK (pin_pause_after BETWEEN 3 AND 50);
ALTER TABLE settings ADD COLUMN pin_pause_minutes integer NOT NULL DEFAULT 15 CHECK (pin_pause_minutes BETWEEN 1 AND 120);
ALTER TABLE kds_stations ADD COLUMN ticket_text_size varchar(10) NOT NULL DEFAULT 'NORMAL' CHECK (ticket_text_size IN ('NORMAL', 'BIG', 'HUGE'));
ALTER TABLE kds_stations ADD COLUMN ticket_notes_red boolean NOT NULL DEFAULT false;
