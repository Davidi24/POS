-- Booking rules talked through with the user (2026-11-02).
-- Staff bookings confirmed at once when a table fits; off = every booking waits for someone who can approve it.
ALTER TABLE settings ADD COLUMN auto_confirm_bookings BOOLEAN NOT NULL DEFAULT TRUE;
-- No answer to "Still coming?": asked again this many hours before (0 = never).
ALTER TABLE settings ADD COLUMN guest_second_reminder_hours INTEGER NOT NULL DEFAULT 6;
-- A booking whose guest never confirmed keeps its table this long (0 = the usual hold).
ALTER TABLE settings ADD COLUMN unconfirmed_hold_minutes INTEGER NOT NULL DEFAULT 15;
-- Who is told about bookings: tomorrow's to check, and the ones staff must look at.
ALTER TABLE settings ADD COLUMN booking_alert_roles VARCHAR(200) NOT NULL DEFAULT 'OWNER,MANAGER';
-- Staff called and couldn't reach the guest.
ALTER TABLE reservations ADD COLUMN attendance_unreachable_at TIMESTAMPTZ;
