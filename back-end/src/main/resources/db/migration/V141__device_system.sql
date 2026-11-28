-- What the device runs ("Linux 6.17", "Android 14"), sent by the app with each sign-in (user, 2026-11-25: "show the operating
-- system and the location of the devices"). Where it is comes from its address, looked up on the server.
ALTER TABLE user_sessions ADD COLUMN device_system varchar(80);
ALTER TABLE auth_login_attempts ADD COLUMN device_system varchar(80);
