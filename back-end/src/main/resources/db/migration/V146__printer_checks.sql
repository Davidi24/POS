-- Whether a printer answers (user, 2026-11-27: "build it"): every till or tablet with the app open checks the restaurant's
-- printers once a minute and says which answered. A printer is online when one answered lately.
ALTER TABLE "device-printer-profiles" ADD COLUMN reached_at timestamptz;
ALTER TABLE "device-printer-profiles" ADD COLUMN checked_at timestamptz;
ALTER TABLE "device-printer-profiles" ADD COLUMN checked_by uuid REFERENCES devices (id) ON DELETE SET NULL;
ALTER TABLE "device-printer-profiles" ADD COLUMN check_problem varchar(300);
