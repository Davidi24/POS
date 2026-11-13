-- A cover picture on each restaurant's and each branch's card in the super admins' lists, so they're easy to tell
-- apart. The single banner picture of V71 is replaced by these.
ALTER TABLE restaurants ADD COLUMN cover_url VARCHAR(2048);
ALTER TABLE branches ADD COLUMN cover_url VARCHAR(2048);
ALTER TABLE users DROP COLUMN home_banner_url;
