-- Restaurant info's last round (user, 2026-11-23): where guests find the restaurant online, a branch's exact spot, the
-- receipts' design, and the brand colours taken out ("remove these colours").
ALTER TABLE restaurants
    ADD COLUMN instagram varchar(200),
    ADD COLUMN facebook varchar(200),
    ADD COLUMN tiktok varchar(200),
    ADD COLUMN whatsapp varchar(200),
    ADD COLUMN maps_link varchar(200);

ALTER TABLE "branch-addresses"
    ADD COLUMN latitude numeric(9, 6),
    ADD COLUMN longitude numeric(9, 6),
    ADD CONSTRAINT ck_branch_addresses_coordinates CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    );

-- Both receipts' design (the preliminary bill and the fiscal invoice), as the app describes it; pictures in it are stored
-- like the logo and referenced by their link.
CREATE TABLE restaurant_receipt_designs (
    restaurant_id uuid PRIMARY KEY REFERENCES restaurants (id),
    design text NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL,
    updated_by uuid REFERENCES users (id)
);

ALTER TABLE "restaurant-branding" DROP COLUMN primary_color, DROP COLUMN secondary_color;
