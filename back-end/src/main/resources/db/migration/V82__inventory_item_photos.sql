-- A photo for each stock item, kept in the same picture storage as dish photos.
ALTER TABLE inventory_items ADD COLUMN image_url TEXT;
