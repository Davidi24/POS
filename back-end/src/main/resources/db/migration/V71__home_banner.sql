-- The picture behind the welcome banner on a super admin's restaurant list (each super admin chooses their own).
ALTER TABLE users ADD COLUMN home_banner_url VARCHAR(2048);
