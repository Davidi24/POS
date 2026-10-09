-- Dish order inside its online section (the online menu's own order, separate from the staff menus).
ALTER TABLE "menu-items"
    ADD COLUMN online_display_order integer NOT NULL DEFAULT 0;
