-- A place's own picture (Inventory → Stock → a place), uploaded by the restaurant; null = its chosen or default icon.
ALTER TABLE inventory_locations
    ADD COLUMN image_url TEXT;
