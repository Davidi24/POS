-- Items like bottled drinks are served straight from the counter and never go to the kitchen.
-- Existing items keep today's behavior (sent to the kitchen); managers switch it off per item.
ALTER TABLE "menu-items"
    ADD COLUMN send_to_kitchen boolean NOT NULL DEFAULT true;
