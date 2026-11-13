-- Which dishes appear in the customer-facing online menu (and can be pre-ordered online).
-- Off by default: nothing goes online until staff switch it on per item.
ALTER TABLE "menu-items"
    ADD COLUMN show_online boolean NOT NULL DEFAULT false;
