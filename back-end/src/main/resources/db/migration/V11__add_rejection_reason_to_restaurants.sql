ALTER TABLE restaurants
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500);
