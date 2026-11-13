-- Which menus waiters see (POS Menu and the till), chosen in Menu → "Menus shown". Every menu starts shown.
ALTER TABLE menus ADD COLUMN shown_to_waiters BOOLEAN NOT NULL DEFAULT TRUE;

-- "Special menus only": waiters see just the special (event) menus. One row per restaurant, created on first save.
CREATE TABLE menu_waiter_views (
    restaurant_id      UUID PRIMARY KEY REFERENCES restaurants (id),
    special_menus_only BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by         UUID        REFERENCES users (id)
);
