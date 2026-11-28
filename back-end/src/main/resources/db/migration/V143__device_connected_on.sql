-- Which device a till or tablet is connected on now, as that device says it (user, 2026-11-27: "make it say who has it"):
-- its name ("Linux computer (fedora)") and system ("Linux 6.17"). Cleared whenever the connection goes.
ALTER TABLE devices ADD COLUMN connected_device_name varchar(150);
ALTER TABLE devices ADD COLUMN connected_device_system varchar(80);
